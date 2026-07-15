package com.migrationreport.service;

import com.migrationreport.dto.DeliverableRequest;
import com.migrationreport.dto.config.TenantConfig;
import com.migrationreport.exception.ResourceNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DeliverableService {

    private final JdbcTemplate jdbcTemplate;
    private final ConfigurationService configurationService;
    private final String statusColumn;
    private final String dateColumn;
    private final String createdDateColumn;
    private final String contentSizeColumn;
    private final SearchService searchService;
    
    @org.springframework.beans.factory.annotation.Autowired
    private com.migrationreport.dialect.SqlDialect dialect;

    public DeliverableService(
            JdbcTemplate jdbcTemplate,
            ConfigurationService configurationService,
            @Value("${search.status-column}") String statusColumn,
            @Value("${search.date-column:MIGRATED_DATE}") String dateColumn,
            @Value("${search.system-columns.created-date:CREATE_DATE}") String createdDateColumn,
            @Value("${search.system-columns.content-size:CONTENT_SIZE}") String contentSizeColumn,
            SearchService searchService) {
        this.jdbcTemplate = jdbcTemplate;
        this.configurationService = configurationService;
        this.statusColumn = statusColumn.toLowerCase();
        this.dateColumn = dateColumn.toLowerCase();
        this.createdDateColumn = createdDateColumn.toLowerCase();
        this.contentSizeColumn = contentSizeColumn.toLowerCase();
        this.searchService = searchService;
    }

    public List<Map<String, Object>> getMigrationReport(DeliverableRequest req) {
        List<Map<String, Object>> result = new ArrayList<>();

        // Determine which apps to query
        List<TenantConfig.ApplicationConfig> appsToQuery = new ArrayList<>();
        List<TenantConfig.ApplicationConfig> allApps = configurationService.getCachedConfig().getApplications();
        
        if (req.getApplicationName() != null && !req.getApplicationName().trim().isEmpty()) {
            String searchName = req.getApplicationName().trim();
            for (TenantConfig.ApplicationConfig app : allApps) {
                if (app.getAppId().equalsIgnoreCase(searchName) || app.getAppName().equalsIgnoreCase(searchName)) {
                    appsToQuery.add(app);
                    break;
                }
            }
        } else {
            appsToQuery.addAll(allApps);
        }

        boolean isAggregated = req.getMigrationStatus() == null 
                || req.getMigrationStatus().trim().isEmpty() 
                || req.getMigrationStatus().equalsIgnoreCase("All");

        for (TenantConfig.ApplicationConfig app : appsToQuery) {
            String schema = app.getSchema() != null && !app.getSchema().isEmpty() ? app.getSchema() + "." : app.getAppId() + ".";
            if (app.getClassifiedTables() == null || app.getClassifiedTables().get("staging") == null || app.getClassifiedTables().get("staging").isEmpty()) {
                throw new ResourceNotFoundException("Staging table configuration missing for application: " + app.getAppId());
            }
            String stagingTableName = app.getClassifiedTables().get("staging").get(0);
            String table = schema + stagingTableName;
            String appDisplayName = app.getAppName();
            
            try {
                if (isAggregated) {
                    List<Map<String, Object>> rows = queryAppAggregated(app.getAppId(), table, appDisplayName, req);
                    result.addAll(rows);
                } else {
                    List<Map<String, Object>> rows = queryAppDetailed(app.getAppId(), table, appDisplayName, req);
                    result.addAll(rows);
                }
            } catch (Exception e) {
                System.err.println("DeliverableService: skipping table " + table + ": " + e.getMessage());
            }
        }
        return result;
    }

    private List<Map<String, Object>> queryAppAggregated(String appIdStr, String table, String appDisplayName, DeliverableRequest req) {
        String sc = configurationService.getSystemColumn(appIdStr, "status", statusColumn);
        String cs = configurationService.getSystemColumn(appIdStr, "content-size", contentSizeColumn);

        String sizeSum = "COALESCE(SUM(COALESCE(" + dialect.castToNumeric(cs) + ",0))/1073741824.0,0)";
        String sizeOk  = "COALESCE(SUM(CASE WHEN LOWER(" + sc + ") IN ('success','migrated') THEN COALESCE(" + dialect.castToNumeric(cs) + ",0) ELSE 0 END)/1073741824.0,0)";

        String runtimeExpr = "0.0 AS migrationruntimedays";
        if (req.getEndDate() != null && !req.getEndDate().trim().isEmpty()) {
            runtimeExpr = dialect.calculateEpochDifferenceDays("migrated_date", "migrated_date") + " AS migrationruntimedays";
        }

        StringBuilder sql = new StringBuilder(
            "SELECT object_class_id AS documentclass," +
            " COUNT(*) AS totaldocuments," +
            " " + sizeSum + " AS totalfilesizegb," +
            " COUNT(CASE WHEN LOWER(" + sc + ") IN ('success','migrated') THEN 1 END) AS extractedfilenet," +
            " COUNT(CASE WHEN LOWER(" + sc + ") = 'failed' THEN 1 END) AS extractionfailed," +
            " COUNT(CASE WHEN LOWER(" + sc + ") NOT IN ('success','migrated','failed') THEN 1 END) AS remaining," +
            " " + sizeOk + " AS extractedfilesizegb," +
            " CASE WHEN COUNT(*)>0 THEN ROUND(COUNT(CASE WHEN LOWER(" + sc + ") IN ('success','migrated') THEN 1 END)*100.0/COUNT(*),2) ELSE 0 END AS percentcompletion," +
            " CASE WHEN COUNT(*)>0 THEN ROUND(COUNT(CASE WHEN LOWER(" + sc + ") = 'failed' THEN 1 END)*100.0/COUNT(*),2) ELSE 0 END AS percentfailed," +
            " " + runtimeExpr +
            " FROM " + table + " WHERE 1=1"
        );

        List<Object> params = new ArrayList<>();

        if (req.getDocumentClass() != null && !req.getDocumentClass().trim().isEmpty() && !req.getDocumentClass().equalsIgnoreCase("All")) {
            String appId = table.substring(0, table.indexOf("."));
            sql.append(" AND object_class_id IN (SELECT object_id FROM ").append(appId).append(".classdef WHERE LOWER(symbolic_name) = LOWER(?))");
            params.add(req.getDocumentClass().trim());
        }
        if (req.getCreatedDate() != null && !req.getCreatedDate().trim().isEmpty()) {
            String currentCreatedDateColumn = configurationService.getSystemColumn(appIdStr, "created-date", createdDateColumn);
            sql.append(" AND ").append(dialect.castToDate(currentCreatedDateColumn)).append(" = ").append(dialect.castToDate("?"));
            params.add(req.getCreatedDate().trim());
        }
        if (req.getStartDate() != null && !req.getStartDate().trim().isEmpty()) {
            String currentDateColumn = configurationService.getSystemColumn(appIdStr, "date", dateColumn);
            sql.append(" AND ").append(dialect.castToTimestamp(currentDateColumn)).append(" >= ").append(dialect.castParameterToTimestamp());
            params.add(req.getStartDate().trim());
        }
        if (req.getEndDate() != null && !req.getEndDate().trim().isEmpty()) {
            String currentDateColumn = configurationService.getSystemColumn(appIdStr, "date", dateColumn);
            sql.append(" AND ").append(dialect.castToTimestamp(currentDateColumn)).append(" <= ").append(dialect.castParameterToTimestamp());
            params.add(req.getEndDate().trim());
        }

        sql.append(" GROUP BY object_class_id ORDER BY object_class_id");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<Map<String, Object>> result = new ArrayList<>();
        String appId = table.substring(0, table.indexOf("."));
        Map<String, String> classMap = getClassIdToSymbolicNameMap(appId);
        for (Map<String, Object> row : rows) {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("isAggregated", true);
            map.put("objectStore", appDisplayName);
            String classId = str(row.get("documentclass"));
            String className = classMap.getOrDefault(classId.toUpperCase(), classId);
            map.put("documentClass", className);
            map.put("totalDocuments", toLong(row.get("totaldocuments")));
            map.put("totalFileSizeGb", toDouble(row.get("totalfilesizegb")));
            map.put("extractedFileNet", toLong(row.get("extractedfilenet")));
            map.put("extractionFailed", toLong(row.get("extractionfailed")));
            map.put("remaining", toLong(row.get("remaining")));
            map.put("extractedFileSizeGb", toDouble(row.get("extractedfilesizegb")));
            map.put("percentCompletion", toDouble(row.get("percentcompletion")));
            map.put("percentFailed", toDouble(row.get("percentfailed")));
            map.put("runTimeDays", toDouble(row.get("migrationruntimedays")));
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> queryAppDetailed(String appIdStr, String table, String appDisplayName, DeliverableRequest req) {
        String sc = configurationService.getSystemColumn(appIdStr, "status", statusColumn);
        String selectCols = searchService.buildSelectClauseForTable(table, null);

        StringBuilder sql = new StringBuilder("SELECT " + selectCols + " FROM " + table + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (req.getDocumentClass() != null && !req.getDocumentClass().trim().isEmpty() && !req.getDocumentClass().equalsIgnoreCase("All")) {
            String appId = table.substring(0, table.indexOf("."));
            sql.append(" AND object_class_id IN (SELECT object_id FROM ").append(appId).append(".classdef WHERE LOWER(symbolic_name) = LOWER(?))");
            params.add(req.getDocumentClass().trim());
        }
        if (req.getCreatedDate() != null && !req.getCreatedDate().trim().isEmpty()) {
            String currentCreatedDateColumn = configurationService.getSystemColumn(appIdStr, "created-date", createdDateColumn);
            sql.append(" AND ").append(dialect.castToDate(currentCreatedDateColumn)).append(" = ").append(dialect.castToDate("?"));
            params.add(req.getCreatedDate().trim());
        }
        if (req.getStartDate() != null && !req.getStartDate().trim().isEmpty()) {
            String currentDateColumn = configurationService.getSystemColumn(appIdStr, "date", dateColumn);
            sql.append(" AND ").append(dialect.castToTimestamp(currentDateColumn)).append(" >= ").append(dialect.castParameterToTimestamp());
            params.add(req.getStartDate().trim());
        }
        if (req.getEndDate() != null && !req.getEndDate().trim().isEmpty()) {
            String currentDateColumn = configurationService.getSystemColumn(appIdStr, "date", dateColumn);
            sql.append(" AND ").append(dialect.castToTimestamp(currentDateColumn)).append(" <= ").append(dialect.castParameterToTimestamp());
            params.add(req.getEndDate().trim());
        }
        if (req.getMigrationStatus() != null && !req.getMigrationStatus().trim().isEmpty()
                && !req.getMigrationStatus().equalsIgnoreCase("All")) {
            if (req.getMigrationStatus().equalsIgnoreCase("Success")) {
                sql.append(" AND LOWER(" + sc + ") IN ('success', 'migrated')");
            } else if (req.getMigrationStatus().equalsIgnoreCase("Failed")) {
                sql.append(" AND LOWER(" + sc + ") = 'failed'");
            } else {
                sql.append(" AND LOWER(" + sc + ") = LOWER(?)");
                params.add(req.getMigrationStatus().trim());
            }
        }

        sql.append(dialect.getLimitSql(5000));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<Map<String, Object>> result = new ArrayList<>();
        String appId = table.substring(0, table.indexOf("."));
        Map<String, String> classMap = getClassIdToSymbolicNameMap(appId);
        for (Map<String, Object> row : rows) {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("isAggregated", false);
            map.put("objectStore", appDisplayName);
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (key.equalsIgnoreCase("object_class_id") && val != null) {
                    val = classMap.getOrDefault(val.toString().toUpperCase(), val.toString());
                }
                map.put(key.toLowerCase(), val);
                map.put(key, val);
            }
            result.add(map);
        }
        return result;
    }

    private String str(Object v) { return v == null ? "" : v.toString(); }
    private Long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }
    private Double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
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
