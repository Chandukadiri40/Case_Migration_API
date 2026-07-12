package com.db_search.service;

import com.db_search.dto.DiscoveryCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DiscoveryService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String buildWhereClause(DiscoveryCriteria criteria, List<Object> params, String tableAlias) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        
        if (criteria.getDocumentClasses() != null && !criteria.getDocumentClasses().isEmpty()) {
            where.append(" AND cd.symbolic_name IN (");
            where.append(criteria.getDocumentClasses().stream().map(c -> "?").collect(Collectors.joining(",")));
            where.append(")");
            params.addAll(criteria.getDocumentClasses());
        }
        
        if (criteria.getCreatedFrom() != null) {
            where.append(" AND CAST(").append(tableAlias).append(".create_date AS TIMESTAMP) >= ?");
            params.add(java.sql.Timestamp.valueOf(criteria.getCreatedFrom() + " 00:00:00"));
        }
        
        if (criteria.getCreatedTo() != null) {
            where.append(" AND CAST(").append(tableAlias).append(".create_date AS TIMESTAMP) <= ?");
            params.add(java.sql.Timestamp.valueOf(criteria.getCreatedTo() + " 23:59:59"));
        }
        
        if (criteria.getMimeTypes() != null && !criteria.getMimeTypes().isEmpty()) {
            where.append(" AND ").append(tableAlias).append(".mime_type IN (");
            where.append(criteria.getMimeTypes().stream().map(c -> "?").collect(Collectors.joining(",")));
            where.append(")");
            params.addAll(criteria.getMimeTypes());
        }

        return where.toString();
    }

    public List<String> getDocumentClasses(String appId) {
        String schema = appId != null && !appId.isEmpty() ? appId + "." : "";
        String sql = "SELECT DISTINCT symbolic_name FROM " + schema + "classdef ORDER BY symbolic_name";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    public List<Map<String, Object>> executeReport(String endpoint, DiscoveryCriteria criteria) {
        String schema = criteria.getAppId() != null && !criteria.getAppId().isEmpty() ? criteria.getAppId() + "." : "";
        String sourceTable = schema + "docversion_source";
        
        List<Object> params = new ArrayList<>();
        String where = buildWhereClause(criteria, params, "dv");
        String sql = "";

        switch (endpoint) {
            case "doc-count":
                sql = "SELECT cd.symbolic_name AS class_name, MIN(dv.create_date) AS earliest_created, MAX(dv.create_date) AS latest_created, " +
                      "COUNT(dv.object_id) AS total_documents, " +
                      "COALESCE(SUM(CAST(dv.content_size AS numeric)), 0) AS total_size_bytes, " +
                      "COALESCE(MIN(CAST(dv.content_size AS numeric)), 0) AS min_size_bytes, " +
                      "COALESCE(MAX(CAST(dv.content_size AS numeric)), 0) AS max_size_bytes, " +
                      "CAST(COALESCE(SUM(CAST(dv.content_size AS numeric)), 0) / 1073741824.0 AS numeric(15, 6)) AS total_size_gb " +
                      "FROM " + sourceTable + " dv " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " AND dv.create_date IS NOT NULL " +
                      "GROUP BY cd.symbolic_name " +
                      "ORDER BY class_name";
                break;
                
            case "doc-year-wise":
                sql = "SELECT cd.symbolic_name AS class_name, EXTRACT(YEAR FROM CAST(dv.create_date AS TIMESTAMP)) AS creation_year, " +
                      "COUNT(dv.object_id) AS total_documents " +
                      "FROM " + sourceTable + " dv " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " AND dv.create_date IS NOT NULL " +
                      "GROUP BY cd.symbolic_name, EXTRACT(YEAR FROM CAST(dv.create_date AS TIMESTAMP)) " +
                      "ORDER BY cd.symbolic_name, creation_year";
                break;
                
            case "doc-year-month":
            case "custom-object-trend":
                String baseTable = endpoint.equals("custom-object-trend") ? (schema + "customobject") : sourceTable;
                sql = "SELECT cd.symbolic_name AS class_name, EXTRACT(YEAR FROM CAST(dv.create_date AS TIMESTAMP)) AS creation_year, " +
                      "EXTRACT(MONTH FROM CAST(dv.create_date AS TIMESTAMP)) AS creation_month, COUNT(dv.object_id) AS total_documents " +
                      "FROM " + baseTable + " dv " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " AND dv.create_date IS NOT NULL " +
                      "GROUP BY cd.symbolic_name, EXTRACT(YEAR FROM CAST(dv.create_date AS TIMESTAMP)), EXTRACT(MONTH FROM CAST(dv.create_date AS TIMESTAMP)) " +
                      "ORDER BY cd.symbolic_name, creation_year, creation_month";
                break;
                
            case "doc-mime":
                sql = "SELECT COALESCE(dv.mime_type, 'No MIME Type') AS mime_type, COUNT(*) AS doc_count FROM " + sourceTable + " dv " +
                      "LEFT JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " GROUP BY COALESCE(dv.mime_type, 'No MIME Type') ORDER BY doc_count DESC";
                break;
                
            case "annotation-total":
                sql = "SELECT cd.symbolic_name AS class_name, " +
                      "COUNT(DISTINCT a.annotated_id) AS total_documents_with_annotations, " +
                      "COUNT(a.object_id) AS total_annotations " +
                      "FROM " + schema + "annotation a " +
                      "INNER JOIN " + sourceTable + " dv ON a.annotated_id = dv.object_id " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " " +
                      "GROUP BY cd.symbolic_name ORDER BY total_annotations DESC";
                break;

            case "annotation-mime":
                sql = "SELECT COALESCE(dv.mime_type, 'No MIME Type') AS mime_type, " +
                      "COUNT(DISTINCT a.annotated_id) AS total_documents_with_annotations, " +
                      "COUNT(a.object_id) AS total_annotations " +
                      "FROM " + schema + "annotation a " +
                      "INNER JOIN " + sourceTable + " dv ON a.annotated_id = dv.object_id " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " " +
                      "GROUP BY COALESCE(dv.mime_type, 'No MIME Type') ORDER BY total_annotations DESC";
                break;
                
            case "size-total":
                sql = "SELECT cd.symbolic_name AS class_name, COUNT(dv.object_id) AS total_documents, " +
                      "COALESCE(SUM(CAST(dv.content_size AS numeric)), 0) AS total_size_bytes, " +
                      "CAST(COALESCE(SUM(CAST(dv.content_size AS numeric)), 0) / 1048576.0 AS numeric(15, 2)) AS total_size_mb, " +
                      "CAST(COALESCE(SUM(CAST(dv.content_size AS numeric)), 0) / 1073741824.0 AS numeric(15, 6)) AS total_size_gb " +
                      "FROM " + sourceTable + " dv " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " AND dv.create_date IS NOT NULL " +
                      "GROUP BY cd.symbolic_name " +
                      "ORDER BY class_name";
                break;
                
            case "size-bucket":
                sql = "SELECT CASE WHEN CAST(dv.content_size AS numeric) IS NULL THEN '0. No Content' " +
                      "WHEN CAST(dv.content_size AS numeric) = 0 THEN '1. Zero Bytes' " +
                      "WHEN CAST(dv.content_size AS numeric) BETWEEN 1 AND 102399 THEN '2. Under 100 KB' " +
                      "WHEN CAST(dv.content_size AS numeric) BETWEEN 102400 AND 511999 THEN '3. 100 KB - 500 KB' " +
                      "WHEN CAST(dv.content_size AS numeric) BETWEEN 512000 AND 1048575 THEN '4. 500 KB - 1 MB' " +
                      "WHEN CAST(dv.content_size AS numeric) BETWEEN 1048576 AND 5242879 THEN '5. 1 MB - 5 MB' " +
                      "WHEN CAST(dv.content_size AS numeric) BETWEEN 5242880 AND 10485759 THEN '6. 5 MB - 10 MB' " +
                      "WHEN CAST(dv.content_size AS numeric) BETWEEN 10485760 AND 26214399 THEN '7. 10 MB - 25 MB' " +
                      "WHEN CAST(dv.content_size AS numeric) BETWEEN 26214400 AND 52428799 THEN '8. 25 MB - 50 MB' " +
                      "WHEN CAST(dv.content_size AS numeric) BETWEEN 52428800 AND 104857599 THEN '9. 50 MB - 100 MB' " +
                      "ELSE '10. Over 100 MB' END AS size_range, " +
                      "COUNT(dv.object_id) AS total_documents, " +
                      "COALESCE(SUM(CAST(dv.content_size AS numeric)), 0) AS total_size_bytes, " +
                      "CAST(COALESCE(SUM(CAST(dv.content_size AS numeric)), 0) / 1048576.0 AS numeric(15, 6)) AS total_size_mb, " +
                      "CAST(COALESCE(SUM(CAST(dv.content_size AS numeric)), 0) / 1073741824.0 AS numeric(15, 6)) AS total_size_gb " +
                      "FROM " + sourceTable + " dv " +
                      "LEFT JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where +
                      " GROUP BY size_range ORDER BY size_range";
                break;
                
            case "no-content":
                sql = "SELECT cd.symbolic_name AS class_name, " +
                      "SUM(CASE WHEN dv.content_size IS NULL OR CAST(dv.content_size AS numeric) = 0 THEN 1 ELSE 0 END) AS docs_without_content " +
                      "FROM " + sourceTable + " dv " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where +
                      " GROUP BY cd.symbolic_name ORDER BY docs_without_content DESC";
                break;
                
            case "version-summary":
                sql = "WITH VersionCounts AS (SELECT version_series_id, object_class_id, COUNT(*) AS version_count FROM " + sourceTable + " GROUP BY version_series_id, object_class_id) " +
                      "SELECT cd.symbolic_name AS class_name, COUNT(vc.version_series_id) AS unique_documents, " +
                      "SUM(vc.version_count) AS total_versions, " +
                      "CAST(SUM(vc.version_count) AS numeric) / NULLIF(COUNT(vc.version_series_id), 0) AS avg_versions_per_doc, " +
                      "MAX(vc.version_count) AS max_versions_single_doc " +
                      "FROM VersionCounts vc INNER JOIN " + schema + "classdef cd ON vc.object_class_id = cd.object_id " +
                      "GROUP BY cd.symbolic_name ORDER BY total_versions DESC";
                break;
                
            case "property-defs":
                sql = "SELECT pd.dbg_class_name AS class_name, gpd.symbolic_name AS property_name, pd.dbg_display_name AS display_name, gpd.max_length_string AS data_length " +
                      "FROM " + schema + "propertydefinition pd " +
                      "INNER JOIN " + schema + "globalpropertydef gpd ON pd.parent_prop_id = gpd.object_id " +
                      "ORDER BY class_name, property_name";
                break;
                
            case "element-total":
                sql = "SELECT COUNT(*) AS total_content_elements FROM " + schema + "content";
                break;
                
            case "element-properties":
                java.util.List<String> listTables = java.util.Arrays.asList("listofinteger32", "listofstring", "listofbinary", "listofboolean", "listofdatetime", "listoffloat64", "listofid");
                java.util.List<String> unionQueries = new java.util.ArrayList<>();
                String schemaName = schema.replace(".", "");
                for (String table : listTables) {
                    try {
                        Integer exists = jdbcTemplate.queryForObject("SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?", Integer.class, schemaName, table);
                        if (exists != null && exists == 1) {
                            unionQueries.add("SELECT '" + table + "' AS list_table, COUNT(parent_id) AS row_count FROM " + schema + table);
                        }
                    } catch (Exception ex) {
                        // ignore if table doesn't exist or query fails
                    }
                }
                if (unionQueries.isEmpty()) {
                    sql = "SELECT 'No List Tables' AS list_table, 0 AS row_count WHERE 1=0";
                } else {
                    sql = String.join(" UNION ALL ", unionQueries);
                }
                break;
            case "version-distribution":
                sql = "WITH VersionCounts AS (SELECT dv.version_series_id, cd.symbolic_name AS class_name, COUNT(*) AS version_count " +
                      "FROM " + sourceTable + " dv INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " GROUP BY dv.version_series_id, cd.symbolic_name) " +
                      "SELECT class_name, " +
                      "CASE WHEN version_count = 1 THEN '1 Version' " +
                      "WHEN version_count = 2 THEN '2 Versions' " +
                      "WHEN version_count BETWEEN 3 AND 5 THEN '3-5 Versions' " +
                      "WHEN version_count BETWEEN 6 AND 10 THEN '6-10 Versions' " +
                      "WHEN version_count BETWEEN 11 AND 20 THEN '11-20 Versions' " +
                      "ELSE '20+ Versions' END AS version_bucket, " +
                      "COUNT(version_series_id) AS doc_count " +
                      "FROM VersionCounts " +
                      "GROUP BY class_name, version_bucket ORDER BY class_name, version_bucket";
                break;
            case "retrieval-hex-blob":
                sql = "SELECT SUM(CASE WHEN LENGTH(COALESCE(retrieval_names, '')) > 0 AND LENGTH(COALESCE(retrieval_names, '')) <= 500 THEN 1 ELSE 0 END) AS RN1_Hex_Format_Count, " +
                      "SUM(CASE WHEN LENGTH(COALESCE(retrieval_names, '')) > 500 THEN 1 ELSE 0 END) AS RN1_Blob_Format_Count " +
                      "FROM " + sourceTable + " dv";
                break;
                
            case "component-hex-blob":
                sql = "SELECT SUM(CASE WHEN LENGTH(COALESCE(component_types, '')) > 0 AND LENGTH(COALESCE(component_types, '')) <= 500 THEN 1 ELSE 0 END) AS CT1_Hex_Format_Count " +
                      "FROM " + sourceTable + " dv";
                break;
                
            case "content-hex-blob":
                sql = "SELECT SUM(CASE WHEN LENGTH(COALESCE(content_info, '')) > 0 AND LENGTH(COALESCE(content_info, '')) <= 500 THEN 1 ELSE 0 END) AS CI1_Hex_Format_Count " +
                      "FROM " + sourceTable + " dv";
                break;
                
            default:
                // Fallback basic count for unimplemented endpoints
                sql = "SELECT cd.symbolic_name AS class_name, COUNT(*) as doc_count FROM " + sourceTable + " dv " +
                      "LEFT JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " GROUP BY cd.symbolic_name";
                break;
        }

        try {
            if (!sql.contains("WHERE 1=1")) {
                params.clear();
            }
            return jdbcTemplate.queryForList(sql, params.toArray());
        } catch (Exception e) {
            System.err.println("Error executing dynamic SQL: " + sql);
            e.printStackTrace();
            throw e;
        }
    }
}
