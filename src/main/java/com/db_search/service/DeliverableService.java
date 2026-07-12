package com.db_search.service;

import com.db_search.dto.DeliverableRequest;
import com.db_search.dto.DeliverableRowDTO;
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
            @Value("${search.system-columns.content-size:CONTENT_SIZE}") String contentSizeColumn) {
        this.jdbcTemplate       = jdbcTemplate;
        this.baseStagingTable   = baseStagingTable;
        this.statusColumn       = statusColumn.toLowerCase();
        this.dateColumn         = dateColumn.toLowerCase();
        this.createdDateColumn  = createdDateColumn.toLowerCase();
        this.contentSizeColumn  = contentSizeColumn.toLowerCase();
    }

    public List<DeliverableRowDTO> getMigrationReport(DeliverableRequest req) {
        List<DeliverableRowDTO> result = new ArrayList<>();

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

        for (String appId : schemas) {
            String table = appId + ".docversion_staging";
            String appDisplayName = APP_NAMES.getOrDefault(appId, appId);
            try {
                List<DeliverableRowDTO> rows = queryApp(table, appDisplayName, req);
                result.addAll(rows);
            } catch (Exception e) {
                System.err.println("DeliverableService: skipping schema " + appId + ": " + e.getMessage());
            }
        }
        return result;
    }

    private List<DeliverableRowDTO> queryApp(String table, String appDisplayName, DeliverableRequest req) {
        String sc = "dv." + statusColumn;
        String cs = "dv." + contentSizeColumn;
        
        String schema = "";
        if (table.contains(".")) {
            schema = table.substring(0, table.indexOf('.') + 1);
        }

        String sizeSum = "COALESCE(SUM(COALESCE(CAST(" + cs + " AS numeric),0))/1073741824.0,0)";
        String sizeOk  = "COALESCE(SUM(CASE WHEN " + sc + "='Success' THEN COALESCE(CAST(" + cs + " AS numeric),0) ELSE 0 END)/1073741824.0,0)";

        StringBuilder sql = new StringBuilder(
            "SELECT COALESCE(cd.symbolic_name, CAST(dv.object_class_id AS VARCHAR)) AS documentClass," +
            " COUNT(*) AS totalDocuments," +
            " " + sizeSum + " AS totalFileSizeGb," +
            " COUNT(CASE WHEN " + sc + "='Success' THEN 1 END) AS extractedFileNet," +
            " COUNT(CASE WHEN " + sc + "='Failed' THEN 1 END) AS extractionFailed," +
            " COUNT(CASE WHEN " + sc + " NOT IN ('Success','Failed') THEN 1 END) AS remaining," +
            " " + sizeOk + " AS extractedFileSizeGb," +
            " CASE WHEN COUNT(*)>0 THEN ROUND(COUNT(CASE WHEN " + sc + "='Success' THEN 1 END)*100.0/COUNT(*),2) ELSE 0 END AS percentCompletion," +
            " CASE WHEN COUNT(*)>0 THEN ROUND(COUNT(CASE WHEN " + sc + "='Failed' THEN 1 END)*100.0/COUNT(*),2) ELSE 0 END AS percentFailed" +
            " FROM " + table + " dv " +
            " LEFT JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
            " WHERE 1=1"
        );

        List<Object> params = new ArrayList<>();

        if (req.getDocumentClass() != null && !req.getDocumentClass().trim().isEmpty()) {
            sql.append(" AND LOWER(COALESCE(cd.symbolic_name, CAST(dv.object_class_id AS VARCHAR))) LIKE LOWER(?)");
            params.add("%" + req.getDocumentClass().trim() + "%");
        }
        if (req.getCreatedDate() != null && !req.getCreatedDate().trim().isEmpty()) {
            sql.append(" AND CAST(dv." + createdDateColumn + " AS date) = CAST(? AS date)");
            params.add(req.getCreatedDate().trim());
        }
        if (req.getStartDate() != null && !req.getStartDate().trim().isEmpty()) {
            sql.append(" AND dv." + dateColumn + " >= ?");
            params.add(req.getStartDate().trim());
        }
        if (req.getEndDate() != null && !req.getEndDate().trim().isEmpty()) {
            sql.append(" AND dv." + dateColumn + " <= ?");
            params.add(req.getEndDate().trim());
        }
        if (req.getMigrationStatus() != null && !req.getMigrationStatus().trim().isEmpty()
                && !req.getMigrationStatus().equalsIgnoreCase("All")) {
            sql.append(" AND " + sc + " = ?");
            params.add(req.getMigrationStatus().trim());
        }

        sql.append(" GROUP BY COALESCE(cd.symbolic_name, CAST(dv.object_class_id AS VARCHAR)) ORDER BY COALESCE(cd.symbolic_name, CAST(dv.object_class_id AS VARCHAR))");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<DeliverableRowDTO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            DeliverableRowDTO dto = new DeliverableRowDTO();
            dto.setObjectStore(appDisplayName);
            dto.setDocumentClass(str(row.get("documentclass")));
            dto.setTotalDocuments(toLong(row.get("totaldocuments")));
            dto.setTotalFileSizeGb(toDouble(row.get("totalfilesizegb")));
            dto.setExtractedFileNet(toLong(row.get("extractedfilenet")));
            dto.setExtractionFailed(toLong(row.get("extractionfailed")));
            dto.setRemaining(toLong(row.get("remaining")));
            dto.setExtractedFileSizeGb(toDouble(row.get("extractedfilesizegb")));
            dto.setPercentCompletion(toDouble(row.get("percentcompletion")));
            dto.setPercentFailed(toDouble(row.get("percentfailed")));
            result.add(dto);
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
}