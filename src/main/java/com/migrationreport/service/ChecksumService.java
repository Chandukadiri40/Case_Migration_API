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

@Slf4j
@Service
public class ChecksumService {

    private final JdbcTemplate jdbcTemplate;
    private final String checksumTable;
    private final String dateColumn;

    private final ConfigurationService configurationService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.migrationreport.dialect.SqlDialect dialect;

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

        String schema = request.getAppId().trim() + ".";
        com.migrationreport.dto.config.TenantConfig.ApplicationConfig appConfig = configurationService.getApplicationConfig(request.getAppId().trim());
        if (appConfig != null && appConfig.getSchema() != null && !appConfig.getSchema().trim().isEmpty()) {
            schema = appConfig.getSchema().trim() + ".";
        }
        
        if (appConfig == null || appConfig.getClassifiedTables() == null || appConfig.getClassifiedTables().get("staging") == null || appConfig.getClassifiedTables().get("staging").isEmpty()) {
            throw new com.migrationreport.exception.ResourceNotFoundException("Staging table configuration missing for application: " + request.getAppId());
        }
        String stagingTableName = appConfig.getClassifiedTables().get("staging").get(0);

        String currentChecksumTable = checksumTable;
        if (appConfig != null && appConfig.getClassifiedTables() != null && appConfig.getClassifiedTables().get("product") != null) {
            currentChecksumTable = appConfig.getClassifiedTables().get("product").stream().filter(t -> t.toLowerCase().contains("checksum")).findFirst().orElse(checksumTable);
        }

        String targetStagingTable = schema + stagingTableName;

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
           .append("c.documentid, ")
           .append("c.checksumbefore, ")
           .append("c.checksumafter, ")
           .append("c.filename, ")
           .append("c.checksum_status, ")
           .append("s.* ")
           .append("FROM ").append(schema).append(currentChecksumTable).append(" c ")
           .append("INNER JOIN ").append(targetStagingTable).append(" s ")
           .append("ON c.documentid = s.object_id ")
           .append("WHERE 1=1");

        List<Object> params = new ArrayList<>();

        sql.append(" AND LOWER(s.migration_status) IN ('success', 'migrated')");

        if (request.getDocumentClass() != null && !request.getDocumentClass().trim().isEmpty() && !request.getDocumentClass().equalsIgnoreCase("All")) {
            sql.append(" AND s.object_class_id IN (SELECT object_id FROM ").append(schema).append("classdef WHERE LOWER(symbolic_name) = LOWER(?))");
            params.add(request.getDocumentClass().trim());
        }

        String currentDateColumn = configurationService.getSystemColumn(request.getAppId().trim(), "date", dateColumn);
        if (request.getFromDate() != null && !request.getFromDate().trim().isEmpty()) {
            sql.append(" AND ").append(dialect.castToTimestamp("s." + currentDateColumn)).append(" >= ").append(dialect.castParameterToTimestamp());
            params.add(request.getFromDate().trim());
        }

        if (request.getToDate() != null && !request.getToDate().trim().isEmpty()) {
            sql.append(" AND ").append(dialect.castToTimestamp("s." + currentDateColumn)).append(" <= ").append(dialect.castParameterToTimestamp());
            params.add(request.getToDate().trim());
        }

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
            Map<String, Object> record = new HashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (key.equalsIgnoreCase("object_class_id") && val != null) {
                    val = classMap.getOrDefault(val.toString().toUpperCase(), val.toString());
                }
                record.put(key.toLowerCase(), val);
                record.put(key, val);
            }
            records.add(record);

            String chkStatus = (String) record.get("checksum_status");
            if ("Completed".equalsIgnoreCase(chkStatus)) {
                completed++;
            } else {
                pending++;
            }

            String migStatus = (String) record.get("migration_status");
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
    private String findClassIdBySymbolicName(String appId, String symbolicName) {
        if (symbolicName == null || symbolicName.trim().isEmpty() || symbolicName.equalsIgnoreCase("All")) {
            return null;
        }
        try {
            String sql = "SELECT object_id FROM " + appId + ".classdef WHERE LOWER(symbolic_name) = LOWER(?)" + dialect.getLimitSql(1);
            return jdbcTemplate.queryForObject(sql, String.class, symbolicName.trim());
        } catch (Exception e) {
            return symbolicName;
        }
    }
    private Map<String, String> getClassIdToSymbolicNameMap(String appId) {
        Map<String, String> map = new java.util.HashMap<>();
        try {
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
            System.err.println("getClassIdToSymbolicNameMap error: " + e.getMessage());
        }
        return map;
    }
}
