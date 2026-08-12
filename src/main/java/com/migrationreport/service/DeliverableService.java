package com.migrationreport.service;

import com.migrationreport.dto.DeliverableRequest;
import com.migrationreport.dto.config.TenantConfig;
import com.migrationreport.exception.ResourceNotFoundException;
import com.migrationreport.dto.PaginatedResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.HashMap;
import com.migrationreport.dialect.SqlDialect;

@Slf4j
@Service
public class DeliverableService {

    private static final String KEY_STAGING = "staging";
    private static final String SQL_COUNT_CASE_LOWER = " COUNT(CASE WHEN LOWER(";
    private static final String SQL_AND_LOWER = " AND LOWER(";
    private static final String SQL_AND = " AND ";
    private static final String MIGRATED_DATE_KEY = "migrated_date";
    private static final String CLASSDEF_KEY = "classdef";
    private static final String CLASS_ID_COL_KEY = "class-id-col";
    private static final String OBJECT_CLASS_ID = "object_class_id";
    private static final String SQL_FROM_SPACE = " FROM ";

    private final JdbcTemplate jdbcTemplate;
    private final ConfigurationService configurationService;
    private final String statusColumn;
    private final String dateColumn;
    private final String createdDateColumn;
    private final String contentSizeColumn;
    private final SearchService searchService;
    
    @Autowired
    private SqlDialect dialect;

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

    @SuppressWarnings("java:S3776")
    public PaginatedResponse<List<Map<String, Object>>> getMigrationReport(DeliverableRequest req) {
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

        long totalRecords = 0;

        for (TenantConfig.ApplicationConfig app : appsToQuery) {
            String schema = app.getSchema() != null && !app.getSchema().isEmpty() ? app.getSchema() + "." : app.getAppId() + ".";
            if (app.getClassifiedTables() == null || app.getClassifiedTables().get(KEY_STAGING) == null || app.getClassifiedTables().get(KEY_STAGING).isEmpty()) {
                throw new ResourceNotFoundException("Staging table configuration missing for application: " + app.getAppId());
            }
            String stagingTableName = app.getClassifiedTables().get(KEY_STAGING).get(0);
            String table = schema + stagingTableName;
            String objStoreName = (app.getObjectStore() != null) ? app.getObjectStore() : app.getAppName();
            
            try {
                if (isAggregated) {
                    List<Map<String, Object>> rows = queryAppAggregated(app.getAppId(), table, objStoreName, req);
                    result.addAll(rows);
                    totalRecords += rows.size();
                } else {
                    PaginatedResponse<List<Map<String, Object>>> pageResult = queryAppDetailed(app.getAppId(), table, objStoreName, req);
                    result.addAll(pageResult.getData());
                    totalRecords += pageResult.getTotalRecords();
                }
            } catch (Exception e) {
                log.error("DeliverableService: skipping table {}: {}", table, e.getMessage());
            }
        }
        return new PaginatedResponse<>(totalRecords, result);
    }

    private List<Map<String, Object>> queryAppAggregated(String appIdStr, String table, String appDisplayName, DeliverableRequest req) {
        String sc = configurationService.getSystemColumn(appIdStr, "status", statusColumn);
        String cs = configurationService.getSystemColumn(appIdStr, "content-size", contentSizeColumn);

        String csClean = "NULLIF(" + cs + ", '')";
        String sizeSum = "COALESCE(SUM(COALESCE(" + dialect.castToNumeric(csClean) + ",0))/1073741824.0,0)";
        String sizeOk  = "COALESCE(SUM(CASE WHEN LOWER(" + sc + ") IN ('success','migrated') THEN COALESCE(" + dialect.castToNumeric(csClean) + ",0) ELSE 0 END)/1073741824.0,0)";

        String runtimeExpr = "0.0 AS migrationruntimedays";
        if (req.getEndDate() != null && !req.getEndDate().trim().isEmpty()) {
            runtimeExpr = dialect.calculateEpochDifferenceDays(MIGRATED_DATE_KEY, MIGRATED_DATE_KEY) + " AS migrationruntimedays";
        }

        String classIdCol = configurationService.getSystemColumn(appIdStr, CLASS_ID_COL_KEY, OBJECT_CLASS_ID);

        StringBuilder sql = new StringBuilder(
            "SELECT " + classIdCol + " AS documentclass," +
            " COUNT(*) AS totaldocuments," +
            " " + sizeSum + " AS totalfilesizegb," +
            SQL_COUNT_CASE_LOWER + sc + ") IN ('success','migrated') THEN 1 END) AS extractedfilenet," +
            SQL_COUNT_CASE_LOWER + sc + ") = 'failed' THEN 1 END) AS extractionfailed," +
            SQL_COUNT_CASE_LOWER + sc + ") NOT IN ('success','migrated','failed') THEN 1 END) AS remaining," +
            " " + sizeOk + " AS extractedfilesizegb," +
            " CASE WHEN COUNT(*)>0 THEN ROUND(COUNT(CASE WHEN LOWER(" + sc + ") IN ('success','migrated') THEN 1 END)*100.0/COUNT(*),2) ELSE 0 END AS percentcompletion," +
            " CASE WHEN COUNT(*)>0 THEN ROUND(COUNT(CASE WHEN LOWER(" + sc + ") = 'failed' THEN 1 END)*100.0/COUNT(*),2) ELSE 0 END AS percentfailed," +
            " " + runtimeExpr +
            SQL_FROM_SPACE + table + " WHERE 1=1"
        );

        List<Object> params = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder();
        appendFilters(whereClause, params, req, appIdStr, table.substring(0, table.indexOf(".")));
        sql.append(whereClause);
        sql.append(" GROUP BY ").append(classIdCol).append(" ORDER BY ").append(classIdCol);

        log.info("Executing Aggregated Query | SQL: {} | Params: {}", sql.toString().toLowerCase(), params);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<Map<String, Object>> result = new ArrayList<>();
        String schemaStr = table.substring(0, table.indexOf("."));
        Map<String, String> classMap = getClassIdToSymbolicNameMap(appIdStr, schemaStr);
        for (Map<String, Object> row : rows) {
            Map<String, Object> map = new HashMap<>();
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

    private String resolveSelectCols(String baseSelectCols, String schemaStr, String tableName, String targetGuidCol) {
        String sqlCols = "SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = LOWER(?)";
        List<String> dbCols = jdbcTemplate.queryForList(sqlCols, String.class, schemaStr, tableName);
        java.util.Set<String> dbColsSet = new java.util.HashSet<>(dbCols);

        StringBuilder selectColsBuilder = new StringBuilder(baseSelectCols);
        String[] extraCols = {"migration_status", MIGRATED_DATE_KEY, "error_message", "error_info", "extracted_status", "extracted_date", targetGuidCol};
        for (String c : extraCols) {
            if (dbColsSet.contains(c.toLowerCase()) && !selectColsBuilder.toString().toLowerCase().contains(c.toLowerCase())) {
                selectColsBuilder.append(", ").append(c);
            }
        }
        return selectColsBuilder.toString();
    }

    private PaginatedResponse<List<Map<String, Object>>> queryAppDetailed(String appIdStr, String table, String appDisplayName, DeliverableRequest req) {
        String sc = configurationService.getSystemColumn(appIdStr, "status", statusColumn);
        String selectCols = searchService.buildSelectClauseForTable(table, null);
        String targetGuidCol = configurationService.getSystemColumn(appIdStr, "target-guid-col", "p8_doc_id");

        String schemaStr = table.substring(0, table.indexOf("."));
        String tableName = table.substring(table.indexOf(".") + 1);
        selectCols = resolveSelectCols(selectCols, schemaStr, tableName, targetGuidCol);

        StringBuilder sql = new StringBuilder("SELECT " + selectCols + SQL_FROM_SPACE + table + " WHERE 1=1");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) " + SQL_FROM_SPACE + table + " WHERE 1=1");
        
        List<Object> params = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder();
        appendFilters(whereClause, params, req, appIdStr, schemaStr);

        if (req.getMigrationStatus() != null && !req.getMigrationStatus().trim().isEmpty()
                && !req.getMigrationStatus().equalsIgnoreCase("All")) {
            if (req.getMigrationStatus().equalsIgnoreCase("Success")) {
                whereClause.append(SQL_AND_LOWER).append(sc).append(") IN ('success', 'migrated')");
            } else if (req.getMigrationStatus().equalsIgnoreCase("Failed")) {
                whereClause.append(SQL_AND_LOWER).append(sc).append(") = 'failed'");
            } else {
                whereClause.append(SQL_AND_LOWER).append(sc).append(") = LOWER(?)");
                params.add(req.getMigrationStatus().trim());
            }
        }

        sql.append(whereClause);
        countSql.append(whereClause);

        long totalRecords = jdbcTemplate.queryForObject(countSql.toString(), Long.class, params.toArray());

        int limit = req.getPageSize();
        int offset = (req.getPage() - 1) * limit;
        sql.append(dialect.getPaginationSql(limit, offset));

        log.info("Executing Detailed Query | SQL: {} | Params: {}", sql.toString().toLowerCase(), params);
        String classIdCol = configurationService.getSystemColumn(appIdStr, CLASS_ID_COL_KEY, OBJECT_CLASS_ID);
        long startDetailed = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        
        String objectIdCol = configurationService.getSystemColumn(appIdStr, "doc-id", "object_id").toLowerCase();
        java.util.List<String> objectIds = new java.util.ArrayList<>();
        for (Map<String, Object> row : rows) {
             Object id = row.get(objectIdCol);
             if (id == null) id = row.get("object_id");
             if (id != null) objectIds.add(id.toString());
        }
        String idsToLog = objectIds.size() > 10 ? objectIds.subList(0, 10).toString() + " (showing first 10)" : objectIds.toString();
        log.info("Found {} record(s) in {}ms. Object IDs: {}", rows.size(), System.currentTimeMillis() - startDetailed, idsToLog);
        
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, String> classMap = getClassIdToSymbolicNameMap(appIdStr, schemaStr);
        for (Map<String, Object> row : rows) {
            Map<String, Object> map = new HashMap<>();
            map.put("isAggregated", false);
            map.put("objectStore", appDisplayName);
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (key.equalsIgnoreCase(classIdCol) && val != null) {
                    val = classMap.getOrDefault(val.toString().toUpperCase(), val.toString());
                }
                map.put(key.toLowerCase(), val);
                map.put(key, val);
            }
            result.add(map);
        }
        return new PaginatedResponse<>(totalRecords, result);
    }

    private void appendFilters(StringBuilder sql, List<Object> params, DeliverableRequest req, String appIdStr, String schemaStr) {
        String classdefTable = configurationService.getSystemTable(appIdStr, CLASSDEF_KEY, CLASSDEF_KEY);
        String symbolicNameCol = configurationService.getSystemColumn(appIdStr, "symbolic-name-col", "symbolic_name");
        String classIdCol = configurationService.getSystemColumn(appIdStr, CLASS_ID_COL_KEY, OBJECT_CLASS_ID);

        if (req.getDocumentClass() != null && !req.getDocumentClass().trim().isEmpty() && !req.getDocumentClass().equalsIgnoreCase("All")) {
            sql.append(SQL_AND).append(classIdCol).append(" IN (SELECT object_id FROM ").append(schemaStr).append(".").append(classdefTable).append(" WHERE LOWER(").append(symbolicNameCol).append(") = LOWER(?))");
            params.add(req.getDocumentClass().trim());
        } else {
            sql.append(SQL_AND).append(classIdCol).append(" IN (SELECT object_id FROM ").append(schemaStr).append(".").append(classdefTable)
               .append(" WHERE CAST(sys_owned_bool AS VARCHAR) IN ('0', 'false', 'FALSE')")
               .append(SQL_AND_LOWER).append(symbolicNameCol).append(") NOT LIKE 'cm%'")
               .append(SQL_AND_LOWER).append(symbolicNameCol).append(") NOT LIKE 'cmxt%'")
               .append(SQL_AND_LOWER).append(symbolicNameCol).append(") NOT LIKE 'preferences%')");
        }
        if (req.getCreatedDate() != null && !req.getCreatedDate().trim().isEmpty()) {
            String currentCreatedDateColumn = configurationService.getSystemColumn(appIdStr, "date", createdDateColumn);
            sql.append(SQL_AND).append(dialect.castToDate(currentCreatedDateColumn)).append(" = ").append(dialect.castToDate("?"));
            params.add(req.getCreatedDate().trim());
        }
        if (req.getStartDate() != null && !req.getStartDate().trim().isEmpty()) {
            String currentDateColumn = configurationService.getSystemColumn(appIdStr, "date", dateColumn);
            sql.append(SQL_AND).append(dialect.castToTimestamp(currentDateColumn)).append(" >= ").append(dialect.castParameterToTimestamp());
            params.add(req.getStartDate().trim());
        }
        if (req.getEndDate() != null && !req.getEndDate().trim().isEmpty()) {
            String currentDateColumn = configurationService.getSystemColumn(appIdStr, "date", dateColumn);
            sql.append(SQL_AND).append(dialect.castToTimestamp(currentDateColumn)).append(" <= ").append(dialect.castParameterToTimestamp());
            params.add(req.getEndDate().trim());
        }
    }
    private String str(Object v) { return v == null ? "" : v.toString(); }
    private Long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number number) return number.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }
    private Double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }
    private Map<String, String> getClassIdToSymbolicNameMap(String appIdStr, String schemaStr) {
        Map<String, String> map = new HashMap<>();
        try {
            String classdefTable = configurationService.getSystemTable(appIdStr, CLASSDEF_KEY, CLASSDEF_KEY);
            String symbolicNameCol = configurationService.getSystemColumn(appIdStr, "symbolic-name-col", "symbolic_name");

            String sql = "SELECT object_id, " + symbolicNameCol + SQL_FROM_SPACE + schemaStr + "." + classdefTable;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                String id = row.get("object_id") != null ? row.get("object_id").toString().toUpperCase() : "";
                
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
}
