package com.db_search.service;

import com.db_search.dto.DeliverableRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DeliverableService {

    private final JdbcTemplate jdbcTemplate;
    private final String baseStagingTable;
    private final String statusColumn;
    private final String dateColumn;
    private final String createdDateColumn;
    private final String contentSizeColumn;
    private final SearchService searchService;

    // apps.json appId -> appName mapping (same as frontend apps.json)
    private static final Map<String, String> APP_NAMES = Map.of(
        "ccol",        "CCOL",
        "genproc",     "GenProc",
        "lynx_bss",    "Lynx BSS",
        "lynx_ls",     "Lynx LS",
        "lynx_oaf",    "Lynx OAF",
        "pronto_clo",  "Pronto CLO",
        "pronto_io",   "Pronto IO",
        "pronto_tulo", "Pronto Tulo",
        "pronto_usl",  "Pronto USL"
    );

    public DeliverableService(
            JdbcTemplate jdbcTemplate,
            @Value("${search.tables.staging}") String baseStagingTable,
            @Value("${search.status-column}") String statusColumn,
            @Value("${search.date-column:MIGRATED_DATE}") String dateColumn,
            @Value("${search.system-columns.created-date:CREATE_DATE}") String createdDateColumn,
            @Value("${search.system-columns.content-size:CONTENT_SIZE}") String contentSizeColumn,
            SearchService searchService) {
        this.jdbcTemplate = jdbcTemplate;
        this.baseStagingTable = baseStagingTable;
        this.statusColumn = statusColumn.toLowerCase();
        this.dateColumn = dateColumn.toLowerCase();
        this.createdDateColumn = createdDateColumn.toLowerCase();
        this.contentSizeColumn = contentSizeColumn.toLowerCase();
        this.searchService = searchService;
    }

    public List<Map<String, Object>> getMigrationReport(DeliverableRequest req) {
        List<Map<String, Object>> result = new ArrayList<>();

        // Determine which app schemas to query
        List<String> schemas = new ArrayList<>();
        if (req.getApplicationName() != null && !req.getApplicationName().trim().isEmpty()) {
            // Find matching appId by name
            String reqName = req.getApplicationName().trim().toLowerCase().replace(" ", "_");
            for (Map.Entry<String, String> e : APP_NAMES.entrySet()) {
                if (e.getKey().equalsIgnoreCase(reqName)
                        || e.getValue().equalsIgnoreCase(req.getApplicationName().trim())
                        || e.getKey().equalsIgnoreCase(req.getApplicationName().trim())) {
                    schemas.add(e.getKey());
                    break;
                }
            }
            // If no match found, try using it directly as a schema
            if (schemas.isEmpty()) {
                schemas.add(req.getApplicationName().trim().toLowerCase());
            }
        } else {
            schemas.addAll(APP_NAMES.keySet());
        }

        boolean isAggregated = req.getMigrationStatus() == null 
                || req.getMigrationStatus().trim().isEmpty() 
                || req.getMigrationStatus().equalsIgnoreCase("All");

        for (String appId : schemas) {
            String table = appId + ".docversion_staging";
            String appDisplayName = APP_NAMES.getOrDefault(appId, appId);
            try {
                if (isAggregated) {
                    List<Map<String, Object>> rows = queryAppAggregated(table, appDisplayName, req);
                    result.addAll(rows);
                } else {
                    List<Map<String, Object>> rows = queryAppRecords(table, appDisplayName, req);
                    result.addAll(rows);
                }
            } catch (Exception e) {
                System.err.println("DeliverableService: skipping schema " + appId + ": " + e.getMessage());
            }
        }
        return result;
    }

    private List<Map<String, Object>> queryAppAggregated(String table, String appDisplayName, DeliverableRequest req) {
        String sc = statusColumn;
        String cs = contentSizeColumn;

        String sizeSum = "COALESCE(SUM(COALESCE(CAST(" + cs + " AS numeric),0))/1073741824.0,0)";
        String sizeOk  = "COALESCE(SUM(CASE WHEN LOWER(" + sc + ") IN ('success','migrated') THEN COALESCE(CAST(" + cs + " AS numeric),0) ELSE 0 END)/1073741824.0,0)";

        StringBuilder sql = new StringBuilder(
            "SELECT object_class_id AS documentclass," +
            " COUNT(*) AS totaldocuments," +
            " " + sizeSum + " AS totalfilesizegb," +
            " COUNT(CASE WHEN LOWER(" + sc + ") IN ('success','migrated') THEN 1 END) AS extractedfilenet," +
            " COUNT(CASE WHEN LOWER(" + sc + ") = 'failed' THEN 1 END) AS extractionfailed," +
            " COUNT(CASE WHEN LOWER(" + sc + ") NOT IN ('success','migrated','failed') THEN 1 END) AS remaining," +
            " " + sizeOk + " AS extractedfilesizegb," +
            " CASE WHEN COUNT(*)>0 THEN ROUND(COUNT(CASE WHEN LOWER(" + sc + ") IN ('success','migrated') THEN 1 END)*100.0/COUNT(*),2) ELSE 0 END AS percentcompletion," +
            " CASE WHEN COUNT(*)>0 THEN ROUND(COUNT(CASE WHEN LOWER(" + sc + ") = 'failed' THEN 1 END)*100.0/COUNT(*),2) ELSE 0 END AS percentfailed" +
            " FROM " + table + " WHERE 1=1"
        );

        List<Object> params = new ArrayList<>();

        if (req.getDocumentClass() != null && !req.getDocumentClass().trim().isEmpty() && !req.getDocumentClass().equalsIgnoreCase("All")) {
            String appId = table.substring(0, table.indexOf("."));
            String classId = findClassIdBySymbolicName(appId, req.getDocumentClass());
            sql.append(" AND object_class_id = ?");
            params.add(classId);
        }
        if (req.getCreatedDate() != null && !req.getCreatedDate().trim().isEmpty()) {
            sql.append(" AND CAST(" + createdDateColumn + " AS date) = CAST(? AS date)");
            params.add(req.getCreatedDate().trim());
        }
        if (req.getStartDate() != null && !req.getStartDate().trim().isEmpty()) {
            sql.append(" AND CAST(" + dateColumn + " AS timestamp) >= CAST(? AS timestamp)");
            params.add(req.getStartDate().trim());
        }
        if (req.getEndDate() != null && !req.getEndDate().trim().isEmpty()) {
            sql.append(" AND CAST(" + dateColumn + " AS timestamp) <= CAST(? AS timestamp)");
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
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> queryAppRecords(String table, String appDisplayName, DeliverableRequest req) {
        String sc = statusColumn;
        String selectCols = searchService.buildSelectClauseForTable(table, null);

        StringBuilder sql = new StringBuilder("SELECT " + selectCols + " FROM " + table + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (req.getDocumentClass() != null && !req.getDocumentClass().trim().isEmpty() && !req.getDocumentClass().equalsIgnoreCase("All")) {
            String appId = table.substring(0, table.indexOf("."));
            String classId = findClassIdBySymbolicName(appId, req.getDocumentClass());
            sql.append(" AND object_class_id = ?");
            params.add(classId);
        }
        if (req.getCreatedDate() != null && !req.getCreatedDate().trim().isEmpty()) {
            sql.append(" AND CAST(" + createdDateColumn + " AS date) = CAST(? AS date)");
            params.add(req.getCreatedDate().trim());
        }
        if (req.getStartDate() != null && !req.getStartDate().trim().isEmpty()) {
            sql.append(" AND CAST(" + dateColumn + " AS timestamp) >= CAST(? AS timestamp)");
            params.add(req.getStartDate().trim());
        }
        if (req.getEndDate() != null && !req.getEndDate().trim().isEmpty()) {
            sql.append(" AND CAST(" + dateColumn + " AS timestamp) <= CAST(? AS timestamp)");
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

        sql.append(" LIMIT 5000");

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
            String sql = "SELECT object_id FROM " + appId + ".classdef WHERE LOWER(symbolic_name) = LOWER(?) LIMIT 1";
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