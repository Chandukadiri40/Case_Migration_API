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
import com.migrationreport.security.SqlIdentifierValidator;

@Slf4j
@Service
public class ChecksumService {

    private final JdbcTemplate jdbcTemplate;
    private final String checksumTable;
    private final String dateColumn;

    private static final String STAGING_KEY = "staging";
    private static final String CHECKSUM_KEY = "checksum";
    private static final String MIGRATION_STATUS_KEY = "migration_status";
    private static final String CLASSDEF_KEY = "classdef";
    private static final String OBJECT_ID = "object_id";

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

        TenantConfig.ApplicationConfig appConfig = configurationService.getApplicationConfig(appId);
        String schema = determineSchema(appId, appConfig);
        String stagingTableName = determineStagingTableName(appConfig, appId);
        String currentChecksumTable = determineChecksumTableName(appConfig);

        String targetStagingTable = schema + stagingTableName;

        String docIdCol = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemColumn(appId, "doc-id", OBJECT_ID));
        String classIdCol = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemColumn(appId, "class-id-col", "object_class_id"));

        StringBuilder sql = buildBaseQuery(schema, currentChecksumTable, targetStagingTable, docIdCol);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, request, schema);

        String finalSql = sql.toString();
        log.info("[CHECKSUM] Executing SQL: {} | Params: {}", finalSql.toLowerCase(), params);
        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(finalSql, params.toArray());
        log.info("[CHECKSUM] Query executed in {}ms. Found {} records.", System.currentTimeMillis() - start, rows.size());

        String objStore = (appConfig.getObjectStore() != null) ? appConfig.getObjectStore() : appConfig.getAppName();
        return buildResponse(rows, appId, schema, classIdCol, objStore);
    }

    private String determineSchema(String appId, TenantConfig.ApplicationConfig appConfig) {
        String schema = SqlIdentifierValidator.validateIdentifier(appId) + ".";
        if (appConfig != null && appConfig.getSchema() != null && !appConfig.getSchema().trim().isEmpty()) {
            schema = SqlIdentifierValidator.validateIdentifier(appConfig.getSchema().trim()) + ".";
        }
        return schema;
    }

    private String determineStagingTableName(TenantConfig.ApplicationConfig appConfig, String appId) {
        if (appConfig == null || appConfig.getClassifiedTables() == null || appConfig.getClassifiedTables().get(STAGING_KEY) == null || appConfig.getClassifiedTables().get(STAGING_KEY).isEmpty()) {
            throw new ResourceNotFoundException("Staging table configuration missing for application: " + appId);
        }
        return SqlIdentifierValidator.validateIdentifier(appConfig.getClassifiedTables().get(STAGING_KEY).get(0));
    }

    private String determineChecksumTableName(TenantConfig.ApplicationConfig appConfig) {
        String currentTable = checksumTable;
        if (appConfig != null && appConfig.getClassifiedTables() != null && appConfig.getClassifiedTables().get(CHECKSUM_KEY) != null && !appConfig.getClassifiedTables().get(CHECKSUM_KEY).isEmpty()) {
            currentTable = appConfig.getClassifiedTables().get(CHECKSUM_KEY).get(0);
        }
        return SqlIdentifierValidator.validateIdentifier(currentTable);
    }

    private ChecksumReportResponse buildResponse(List<Map<String, Object>> rows, String appId, String schema, String classIdCol, String objStore) {
        List<Map<String, Object>> records = new ArrayList<>();
        long completed = 0;
        long pending = 0;
        long migratedInStaging = 0;

        Map<String, String> classMap = getClassIdToSymbolicNameMap(appId, schema);
        String statusCol = configurationService.getSystemColumn(appId, "status", MIGRATION_STATUS_KEY);

        for (Map<String, Object> row : rows) {
            Map<String, Object> rec = processRow(row, classMap, classIdCol, objStore);
            records.add(rec);

            String chkStatus = (String) rec.get("checksum_status");
            if ("Completed".equalsIgnoreCase(chkStatus)) {
                completed++;
            } else {
                pending++;
            }

            String migStatus = (String) rec.get(statusCol.toLowerCase());
            if (migStatus == null && rec.get(MIGRATION_STATUS_KEY) != null) {
                migStatus = (String) rec.get(MIGRATION_STATUS_KEY);
            }
            if ("Migrated".equalsIgnoreCase(migStatus) || "Success".equalsIgnoreCase(migStatus)) {
                migratedInStaging++;
            }
        }

        Map<String, Long> summary = new HashMap<>();
        summary.put("total", (long) rows.size());
        summary.put("completed", completed);
        summary.put("pending", pending);
        summary.put("migratedInStaging", migratedInStaging);

        ChecksumReportResponse response = new ChecksumReportResponse();
        response.setRecords(records);
        response.setSummary(summary);
        return response;
    }

    private Map<String, Object> processRow(Map<String, Object> row, Map<String, String> classMap, String classIdCol, String objStore) {
        Map<String, Object> rec = new HashMap<>();
        rec.put("objectStore", objStore);
        rec.put("object_store", objStore);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (key.equalsIgnoreCase(classIdCol) && val != null) {
                val = classMap.getOrDefault(val.toString().toUpperCase(), val.toString());
            }
            rec.put(key.toLowerCase(), val);
            rec.put(key, val);
        }
        return rec;
    }

    private Map<String, String> getClassIdToSymbolicNameMap(String appIdStr, String schemaStr) {
        Map<String, String> map = new java.util.HashMap<>();
        try {
            String classdefTable = configurationService.getSystemTable(appIdStr, CLASSDEF_KEY, CLASSDEF_KEY);
            String symbolicNameCol = configurationService.getSystemColumn(appIdStr, "symbolic-name-col", "symbolic_name");

            String sql = "SELECT " + OBJECT_ID + ", " + symbolicNameCol + " FROM " + schemaStr + classdefTable;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                String id = row.get(OBJECT_ID) != null ? row.get(OBJECT_ID).toString().toUpperCase() : "";
                
                Object nameObj = row.get(symbolicNameCol);
                if (nameObj == null) {
                    nameObj = row.get(symbolicNameCol.toLowerCase());
                }
                String name = nameObj != null ? nameObj.toString() : "";
                
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
        return SqlIdentifierValidator.validateIdentifier(identifier);
    }

    private StringBuilder buildBaseQuery(String schema, String currentChecksumTable, String targetStagingTable, String docIdCol) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c.documentid, c.checksumbefore, c.checksumafter, c.filename, c.checksum_status, s.* ")
           .append("FROM ").append(schema).append(currentChecksumTable).append(" c ")
           .append("INNER JOIN ").append(targetStagingTable).append(" s ON c.documentid = s.").append(docIdCol).append(" ")
           .append("WHERE 1=1");
        return sql;
    }

    private void appendFilters(StringBuilder sql, List<Object> params, ChecksumReportRequest request, String schema) {
        String appId = request.getAppId().trim();
        String statusCol = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemColumn(appId, "status", MIGRATION_STATUS_KEY));
        sql.append(" AND LOWER(s.").append(statusCol).append(") IN ('success', 'migrated')");

        if (request.getDocumentClass() != null && !request.getDocumentClass().trim().isEmpty() && !request.getDocumentClass().equalsIgnoreCase("All")) {
            String classdefTable = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemTable(appId, CLASSDEF_KEY, CLASSDEF_KEY));
            String symbolicNameCol = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemColumn(appId, "symbolic-name-col", "symbolic_name"));
            String classIdCol = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemColumn(appId, "class-id-col", "object_class_id"));

            sql.append(" AND s.").append(classIdCol).append(" IN (SELECT object_id FROM ").append(schema).append(classdefTable).append(" WHERE LOWER(").append(symbolicNameCol).append(") = LOWER(?))");
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
