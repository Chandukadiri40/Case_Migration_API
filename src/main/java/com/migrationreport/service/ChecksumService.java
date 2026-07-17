package com.migrationreport.service;

import com.migrationreport.dto.ChecksumReportRequest;
import com.migrationreport.dto.ChecksumReportResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import com.migrationreport.dto.config.TenantConfig;
import com.migrationreport.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import com.migrationreport.dialect.SqlDialect;

@Slf4j
@Service
public class ChecksumService {

    private final JdbcTemplate jdbcTemplate;
    private final String checksumTable;
    private final String dateColumn;

    private static final String STAGING_KEY = "staging";

    private final ConfigurationService configurationService;

    @Autowired
    private SqlDialect dialect;

    public ChecksumService(
            JdbcTemplate jdbcTemplate,
            ConfigurationService configurationService,
            @Value("${checksum.table}") String checksumTable,
            @Value("${search.date-column:CREATE_DATE}") String dateColumn) {
        this.jdbcTemplate = jdbcTemplate;
        this.configurationService = configurationService;
        this.checksumTable = checksumTable;
        this.dateColumn = dateColumn.toLowerCase();
    }

    public ChecksumReportResponse getReport(ChecksumReportRequest request) {
        if (request.getAppId() == null || request.getAppId().trim().isEmpty()) {
            throw new IllegalArgumentException("Application (Object Store) is required.");
        }
        String appId = validateIdentifier(request.getAppId().trim());

        String schema = appId + ".";
        TenantConfig.ApplicationConfig appConfig = configurationService.getApplicationConfig(appId);
        if (appConfig != null && appConfig.getSchema() != null && !appConfig.getSchema().trim().isEmpty()) {
            schema = validateIdentifier(appConfig.getSchema().trim()) + ".";
        }
        
        if (appConfig == null || appConfig.getClassifiedTables() == null || appConfig.getClassifiedTables().get(STAGING_KEY) == null || appConfig.getClassifiedTables().get(STAGING_KEY).isEmpty()) {
            throw new ResourceNotFoundException("Staging table configuration missing for application: " + request.getAppId());
        }
        String stagingTableName = appConfig.getClassifiedTables().get(STAGING_KEY).get(0);

        String currentChecksumTable = checksumTable;
        if (appConfig != null && appConfig.getClassifiedTables() != null && appConfig.getClassifiedTables().get("product") != null) {
            currentChecksumTable = appConfig.getClassifiedTables().get("product").stream().filter(t -> t.toLowerCase().contains("checksum")).findFirst().orElse(checksumTable);
        }
        stagingTableName = validateIdentifier(stagingTableName);
        currentChecksumTable = validateIdentifier(currentChecksumTable);

        String targetStagingTable = schema + stagingTableName;

        StringBuilder sql = buildBaseQuery(schema, currentChecksumTable, targetStagingTable);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, request, schema);


        String finalSql = sql.toString();
        log.info("[CHECKSUM] Executing SQL: {} | Params: {}", finalSql, params);
        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(finalSql, params.toArray());
        log.info("[CHECKSUM] Query executed in {}ms. Found {} records.", System.currentTimeMillis() - start, rows.size());

        List<Map<String, Object>> records = new ArrayList<>();
        long total = rows.size();
        long completed = 0;
        long pending = 0;
        long migratedInStaging = 0;

        Map<String, String> classMap = getClassIdToSymbolicNameMap(request.getAppId());
        for (Map<String, Object> row : rows) {
            Map<String, Object> rec = new HashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (key.equalsIgnoreCase("object_class_id") && val != null) {
                    val = classMap.getOrDefault(val.toString().toUpperCase(), val.toString());
                }
                rec.put(key.toLowerCase(), val);
                rec.put(key, val);
            }
            records.add(rec);

            String chkStatus = (String) rec.get("checksum_status");
            if ("Completed".equalsIgnoreCase(chkStatus)) {
                completed++;
            } else {
                pending++;
            }

            String migStatus = (String) rec.get("migration_status");
            if ("Migrated".equalsIgnoreCase(migStatus) || "Success".equalsIgnoreCase(migStatus)) {
                migratedInStaging++;
            }
        }

        Map<String, Long> summary = new HashMap<>();
        summary.put("total", total);
        summary.put("completed", completed);
        summary.put("pending", pending);
        summary.put("migratedInStaging", migratedInStaging);

        ChecksumReportResponse response = new ChecksumReportResponse();
        response.setRecords(records);
        response.setSummary(summary);

        return response;
    }

    private Map<String, String> getClassIdToSymbolicNameMap(String appId) {
        Map<String, String> map = new java.util.HashMap<>();
        try {
            appId = validateIdentifier(appId);
            String sql = "SELECT object_id, symbolic_name FROM " + appId + ".classdef";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                String id = row.get("object_id") != null ? row.get("object_id").toString().toUpperCase() : "";
                String name = row.get("symbolic_name") != null ? row.get("symbolic_name").toString() : "";
                if (!id.isEmpty() && !name.isEmpty()) {
                    map.put(id, name);
                }
            }
        } catch (Exception e) {
            log.error("getClassIdToSymbolicNameMap error: {}", e.getMessage(), e);
        }
        return map;
    }

    private String validateIdentifier(String identifier) {
        if (identifier != null && !identifier.matches("^[a-zA-Z0-9_\\-]+$")) {
            throw new IllegalArgumentException("Invalid identifier format: " + identifier);
        }
        return identifier;
    }

    private StringBuilder buildBaseQuery(String schema, String currentChecksumTable, String targetStagingTable) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c.documentid, c.checksumbefore, c.checksumafter, c.filename, c.checksum_status, s.* ")
           .append("FROM ").append(schema).append(currentChecksumTable).append(" c ")
           .append("INNER JOIN ").append(targetStagingTable).append(" s ON c.documentid = s.object_id ")
           .append("WHERE 1=1");
        return sql;
    }

    private void appendFilters(StringBuilder sql, List<Object> params, ChecksumReportRequest request, String schema) {
        sql.append(" AND LOWER(s.migration_status) IN ('success', 'migrated')");

        if (request.getDocumentClass() != null && !request.getDocumentClass().trim().isEmpty() && !request.getDocumentClass().equalsIgnoreCase("All")) {
            sql.append(" AND s.object_class_id IN (SELECT object_id FROM ").append(schema).append("classdef WHERE LOWER(symbolic_name) = LOWER(?))");
            params.add(request.getDocumentClass().trim());
        }

        String currentDateColumn = configurationService.getSystemColumn(request.getAppId().trim(), "date", dateColumn);
        validateIdentifier(currentDateColumn);

        if (request.getFromDate() != null && !request.getFromDate().trim().isEmpty()) {
            sql.append(" AND ").append(dialect.castToTimestamp("s." + currentDateColumn)).append(" >= ").append(dialect.castParameterToTimestamp());
            params.add(request.getFromDate().trim());
        }

        if (request.getToDate() != null && !request.getToDate().trim().isEmpty()) {
            sql.append(" AND ").append(dialect.castToTimestamp("s." + currentDateColumn)).append(" <= ").append(dialect.castParameterToTimestamp());
            params.add(request.getToDate().trim());
        }
    }
}
