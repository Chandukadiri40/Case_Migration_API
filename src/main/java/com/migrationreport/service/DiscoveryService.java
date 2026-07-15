package com.migrationreport.service;

import com.migrationreport.dto.DiscoveryCriteria;
import com.migrationreport.dto.config.TenantConfig.ApplicationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.migrationreport.exception.DatabaseQueryException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DiscoveryService {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ConfigurationService configurationService;
    
    @Autowired
    private com.migrationreport.dialect.SqlDialect dialect;

    @org.springframework.beans.factory.annotation.Value("${search.system-columns.created-date:CREATE_DATE}")
    private String createdDateColumn;

    private String getSchema(String appId) {
        if (appId == null || appId.isEmpty()) return "";
        ApplicationConfig appConfig = configurationService.getApplicationConfig(appId);
        if (appConfig != null && appConfig.getSchema() != null && !appConfig.getSchema().isEmpty()) {
            return appConfig.getSchema() + ".";
        }
        return appId + "."; // Fallback to legacy
    }

    private String buildWhereClause(DiscoveryCriteria criteria, List<Object> params, String tableAlias) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        
        if (criteria.getDocumentClasses() != null && !criteria.getDocumentClasses().isEmpty()) {
            where.append(" AND cd.symbolic_name IN (");
            where.append(criteria.getDocumentClasses().stream().map(c -> "?").collect(Collectors.joining(",")));
            where.append(")");
            params.addAll(criteria.getDocumentClasses());
        }
        
        if (criteria.getCreatedFrom() != null) {
            where.append(" AND ").append(dialect.castToTimestamp(tableAlias + "." + createdDateColumn)).append(" >= ?");
            params.add(java.sql.Timestamp.valueOf(criteria.getCreatedFrom() + " 00:00:00"));
        }
        
        if (criteria.getCreatedTo() != null) {
            where.append(" AND ").append(dialect.castToTimestamp(tableAlias + "." + createdDateColumn)).append(" <= ?");
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
        String schema = getSchema(appId);
        String sql = "SELECT DISTINCT symbolic_name FROM " + schema + "classdef ORDER BY symbolic_name";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    public List<Map<String, Object>> executeReport(String endpoint, DiscoveryCriteria criteria) {
        String schema = getSchema(criteria.getAppId());
        ApplicationConfig appConfig = configurationService.getApplicationConfig(criteria.getAppId());
        if (appConfig == null || appConfig.getClassifiedTables() == null || appConfig.getClassifiedTables().get("source") == null || appConfig.getClassifiedTables().get("source").isEmpty()) {
            throw new com.migrationreport.exception.ResourceNotFoundException("Source table configuration missing for application: " + criteria.getAppId());
        }
        String targetTable = appConfig.getClassifiedTables().get("source").get(0);
        String sourceTable = schema + targetTable;
        
        List<Object> params = new ArrayList<>();
        String where = buildWhereClause(criteria, params, "dv");
        String sql = "";

        switch (endpoint) {
            case "doc-count":
                sql = "SELECT cd.symbolic_name AS class_name, MIN(dv." + createdDateColumn + ") AS earliest_created, MAX(dv." + createdDateColumn + ") AS latest_created, " +
                      "COUNT(dv.object_id) AS total_documents, " +
                      "COALESCE(SUM(" + dialect.castToNumeric("dv.content_size") + "), 0) AS total_size_bytes, " +
                      "COALESCE(MIN(" + dialect.castToNumeric("dv.content_size") + "), 0) AS min_size_bytes, " +
                      "COALESCE(MAX(" + dialect.castToNumeric("dv.content_size") + "), 0) AS max_size_bytes, " +
                      "CAST(COALESCE(SUM(" + dialect.castToNumeric("dv.content_size") + "), 0) / 1073741824.0 AS numeric(15, 6)) AS total_size_gb " +
                      "FROM " + sourceTable + " dv " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " AND dv." + createdDateColumn + " IS NOT NULL " +
                      "GROUP BY cd.symbolic_name " +
                      "ORDER BY class_name";
                break;
                
            case "doc-year-wise":
                sql = "SELECT cd.symbolic_name AS class_name, " + dialect.extractYear("dv." + createdDateColumn) + " AS creation_year, " +
                      "COUNT(dv.object_id) AS total_documents " +
                      "FROM " + sourceTable + " dv " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " AND dv." + createdDateColumn + " IS NOT NULL " +
                      "GROUP BY cd.symbolic_name, " + dialect.extractYear("dv." + createdDateColumn) + " " +
                      "ORDER BY cd.symbolic_name, creation_year";
                break;
                
            case "doc-year-month":
            case "custom-object-trend":
                String baseTable = endpoint.equals("custom-object-trend") ? (schema + "customobject") : sourceTable;
                sql = "SELECT cd.symbolic_name AS class_name, " + dialect.extractYear("dv." + createdDateColumn) + " AS creation_year, " +
                      dialect.extractMonth("dv." + createdDateColumn) + " AS creation_month, COUNT(dv.object_id) AS total_documents " +
                      "FROM " + baseTable + " dv " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " AND dv." + createdDateColumn + " IS NOT NULL " +
                      "GROUP BY cd.symbolic_name, " + dialect.extractYear("dv." + createdDateColumn) + ", " + dialect.extractMonth("dv." + createdDateColumn) + " " +
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
                      "COALESCE(SUM(" + dialect.castToNumeric("dv.content_size") + "), 0) AS total_size_bytes, " +
                      "CAST(COALESCE(SUM(" + dialect.castToNumeric("dv.content_size") + "), 0) / 1048576.0 AS numeric(15, 2)) AS total_size_mb, " +
                      "CAST(COALESCE(SUM(" + dialect.castToNumeric("dv.content_size") + "), 0) / 1073741824.0 AS numeric(15, 6)) AS total_size_gb " +
                      "FROM " + sourceTable + " dv " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " AND dv." + createdDateColumn + " IS NOT NULL " +
                      "GROUP BY cd.symbolic_name " +
                      "ORDER BY class_name";
                break;
                
            case "size-bucket":
                sql = "SELECT CASE WHEN " + dialect.castToNumeric("dv.content_size") + " IS NULL THEN '0. No Content' " +
                      "WHEN " + dialect.castToNumeric("dv.content_size") + " = 0 THEN '1. Zero Bytes' " +
                      "WHEN " + dialect.castToNumeric("dv.content_size") + " BETWEEN 1 AND 102399 THEN '2. Under 100 KB' " +
                      "WHEN " + dialect.castToNumeric("dv.content_size") + " BETWEEN 102400 AND 511999 THEN '3. 100 KB - 500 KB' " +
                      "WHEN " + dialect.castToNumeric("dv.content_size") + " BETWEEN 512000 AND 1048575 THEN '4. 500 KB - 1 MB' " +
                      "WHEN " + dialect.castToNumeric("dv.content_size") + " BETWEEN 1048576 AND 5242879 THEN '5. 1 MB - 5 MB' " +
                      "WHEN " + dialect.castToNumeric("dv.content_size") + " BETWEEN 5242880 AND 10485759 THEN '6. 5 MB - 10 MB' " +
                      "WHEN " + dialect.castToNumeric("dv.content_size") + " BETWEEN 10485760 AND 26214399 THEN '7. 10 MB - 25 MB' " +
                      "WHEN " + dialect.castToNumeric("dv.content_size") + " BETWEEN 26214400 AND 52428799 THEN '8. 25 MB - 50 MB' " +
                      "WHEN " + dialect.castToNumeric("dv.content_size") + " BETWEEN 52428800 AND 104857599 THEN '9. 50 MB - 100 MB' " +
                      "ELSE '10. Over 100 MB' END AS size_range, " +
                      "COUNT(dv.object_id) AS total_documents, " +
                      "COALESCE(SUM(" + dialect.castToNumeric("dv.content_size") + "), 0) AS total_size_bytes, " +
                      "CAST(COALESCE(SUM(" + dialect.castToNumeric("dv.content_size") + "), 0) / 1048576.0 AS numeric(15, 6)) AS total_size_mb, " +
                      "CAST(COALESCE(SUM(" + dialect.castToNumeric("dv.content_size") + "), 0) / 1073741824.0 AS numeric(15, 6)) AS total_size_gb " +
                      "FROM " + sourceTable + " dv " +
                      "LEFT JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where +
                      " GROUP BY size_range ORDER BY size_range";
                break;
                
            case "no-content":
                sql = "SELECT cd.symbolic_name AS class_name, " +
                      "SUM(CASE WHEN dv.content_size IS NULL OR " + dialect.castToNumeric("dv.content_size") + " = 0 THEN 1 ELSE 0 END) AS docs_without_content " +
                      "FROM " + sourceTable + " dv " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where +
                      " GROUP BY cd.symbolic_name ORDER BY docs_without_content DESC";
                break;
                
            case "version-summary":
                sql = "WITH VersionCounts AS (SELECT version_series_id, object_class_id, COUNT(*) AS version_count FROM " + sourceTable + " GROUP BY version_series_id, object_class_id) " +
                      "SELECT cd.symbolic_name AS class_name, COUNT(vc.version_series_id) AS unique_documents, " +
                      "SUM(vc.version_count) AS total_versions, " +
                      dialect.castToNumeric("SUM(vc.version_count)") + " / NULLIF(COUNT(vc.version_series_id), 0) AS avg_versions_per_doc, " +
                      "MAX(vc.version_count) AS max_versions_single_doc " +
                      "FROM VersionCounts vc INNER JOIN " + schema + "classdef cd ON vc.object_class_id = cd.object_id " +
                      "GROUP BY cd.symbolic_name ORDER BY total_versions DESC";
                break;
                
            case "property-defs":
                params.clear();
                sql = "SELECT pd.dbg_class_name AS class_name, gpd.symbolic_name AS property_name, pd.dbg_display_name AS display_name, " +
                      "CASE CAST(pd.datatype AS VARCHAR) " +
                      "WHEN '0' THEN 'Unspecified' " +
                      "WHEN '1' THEN 'Binary' " +
                      "WHEN '2' THEN 'Boolean' " +
                      "WHEN '3' THEN 'DateTime' " +
                      "WHEN '4' THEN 'Float64' " +
                      "WHEN '5' THEN 'ID' " +
                      "WHEN '6' THEN 'Integer32' " +
                      "WHEN '7' THEN 'Object' " +
                      "WHEN '8' THEN 'String' " +
                      "ELSE CONCAT('Unknown (', CAST(pd.datatype AS VARCHAR), ')') END AS type, " +
                      "gpd.max_length_string AS data_length " +
                      "FROM " + schema + "propertydefinition pd " +
                      "INNER JOIN " + schema + "globalpropertydef gpd ON pd.global_prop_id = gpd.object_id " +
                      "WHERE 1=1 ";
                if (criteria.getDocumentClasses() != null && !criteria.getDocumentClasses().isEmpty()) {
                    sql += "AND pd.dbg_class_name IN (" + criteria.getDocumentClasses().stream().map(c -> "?").collect(Collectors.joining(",")) + ") ";
                    params.addAll(criteria.getDocumentClasses());
                }
                sql += "ORDER BY class_name, property_name";
                break;
                
            case "element-total":
                sql = "SELECT COUNT(*) AS total_content_elements FROM " + schema + "content";
                break;
                
            case "element-class":
                sql = "SELECT cd.symbolic_name AS class_name, COUNT(DISTINCT dv.object_id) AS total_documents, COUNT(c.doc_id) AS total_content_elements " +
                      "FROM " + schema + "content c " +
                      "INNER JOIN " + sourceTable + " dv ON c.doc_id = dv.object_id " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " " +
                      "GROUP BY cd.symbolic_name ORDER BY total_content_elements DESC";
                break;
                
            case "element-properties":
               List<String> listTables = Arrays.asList("listofinteger32", "listofstring", "listofbinary", "listofboolean", "listofdatetime", "listoffloat64", "listofid");
               List<String> unionQueries = new ArrayList<>();
                String schemaName = schema.replace(".", "").toLowerCase();
                for (String table : listTables) {
                    try {
                        Integer exists = jdbcTemplate.queryForObject("SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?", Integer.class, schemaName, table);
                        if (exists != null && exists == 1) {
                            unionQueries.add("SELECT '" + table + "' AS list_table, COUNT(parent_id) AS row_count FROM " + schema + table);
                        }
                    } catch (Exception ex) {
                    	log.error("Error executing dynamic SQL: {}", sql, ex);
                    }
                }
                if (unionQueries.isEmpty()) {
                    sql = "SELECT 'No List Tables' AS list_table, 0 AS row_count WHERE 1=0";
                } else {
                    sql = String.join(" UNION ALL ", unionQueries);
                }
                break;
            case "version-distribution":
                sql = "SELECT cd.symbolic_name AS class_name, " +
                      "COALESCE(dv.major_version_number, '1') || '.' || COALESCE(dv.minor_version_number, '0') AS version_bucket, " +
                      "COUNT(dv.object_id) AS doc_count " +
                      "FROM " + sourceTable + " dv " +
                      "INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                      where + " " +
                      "GROUP BY cd.symbolic_name, COALESCE(dv.major_version_number, '1') || '.' || COALESCE(dv.minor_version_number, '0') " +
                      "ORDER BY cd.symbolic_name, version_bucket";
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
            log.info("[DISCOVERY] Executing Report Endpoint: '{}' | SQL: {} | Params: {}", endpoint, sql, params);
            long start = System.currentTimeMillis();
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, params.toArray());
            log.info("[DISCOVERY] Report executed in {}ms. Found {} records.", System.currentTimeMillis() - start, results.size());
            return results;
        } catch (Exception e) {
            log.error("Error executing dynamic SQL: {}", sql, e);
            throw new DatabaseQueryException("Failed to generate discovery report: " + e.getMessage(), e);
        }
    }
}
