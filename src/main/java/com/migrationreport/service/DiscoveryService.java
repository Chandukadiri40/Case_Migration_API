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
import com.migrationreport.exception.ResourceNotFoundException;
import java.sql.Timestamp;
import org.springframework.beans.factory.annotation.Value;
import com.migrationreport.dialect.SqlDialect;

@Slf4j
@Service
public class DiscoveryService {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private static final String SOURCE_KEY = "source";
    private static final String WHERE_1_1 = " WHERE 1=1";
    private static final String SQL_AND = " AND ";
    private static final String SQL_FROM = " FROM ";
    private static final String SQL_INNER_JOIN = "INNER JOIN ";
    private static final String SQL_LEFT_JOIN = "LEFT JOIN ";
    private static final String SQL_WHEN = "WHEN ";
    private static final String SQL_IS_NOT_NULL = " IS NOT NULL ";
    private static final String SQL_AND_DV = " AND dv.";
    private static final String SQL_CAST_COALESCE_SUM = "CAST(COALESCE(SUM(";
    private static final String COL_DV_CONTENT_SIZE = "dv.content_size";
    private static final String SQL_SELECT_CLASS_NAME = "SELECT cd.symbolic_name AS class_name, ";
    private static final String SQL_CLASSDEF_JOIN_COND = "classdef cd ON dv.object_class_id = cd.object_id ";

    @Autowired
    private ConfigurationService configurationService;
    
    @Autowired
    private SqlDialect dialect;

    @Value("${search.system-columns.created-date:CREATE_DATE}")
    private String createdDateColumn;

    private String validateIdentifier(String identifier) {
        if (identifier != null && !identifier.matches("^[a-zA-Z0-9_\\-]+$")) {
            throw new IllegalArgumentException("Invalid identifier format: " + identifier);
        }
        return identifier;
    }

    private String getSchema(String appId) {
        if (appId == null || appId.isEmpty()) return "";
        ApplicationConfig appConfig = configurationService.getApplicationConfig(appId);
        if (appConfig != null && appConfig.getSchema() != null && !appConfig.getSchema().isEmpty()) {
            return validateIdentifier(appConfig.getSchema()) + ".";
        }
        return validateIdentifier(appId) + "."; // Fallback to legacy
    }

    private String buildWhereClause(DiscoveryCriteria criteria, List<Object> params, String tableAlias) {
        StringBuilder where = new StringBuilder(WHERE_1_1);
        
        if (criteria.getDocumentClasses() != null && !criteria.getDocumentClasses().isEmpty()) {
            where.append(SQL_AND).append("cd.symbolic_name IN (");
            where.append(criteria.getDocumentClasses().stream().map(c -> "?").collect(Collectors.joining(",")));
            where.append(")");
            params.addAll(criteria.getDocumentClasses());
        }
        
        if (criteria.getCreatedFrom() != null) {
            where.append(SQL_AND).append(dialect.castToTimestamp(tableAlias + "." + createdDateColumn)).append(" >= ?");
            params.add(Timestamp.valueOf(criteria.getCreatedFrom() + " 00:00:00"));
        }
        
        if (criteria.getCreatedTo() != null) {
            where.append(SQL_AND).append(dialect.castToTimestamp(tableAlias + "." + createdDateColumn)).append(" <= ?");
            params.add(Timestamp.valueOf(criteria.getCreatedTo() + " 23:59:59"));
        }
        
        if (criteria.getMimeTypes() != null && !criteria.getMimeTypes().isEmpty()) {
            where.append(SQL_AND).append(tableAlias).append(".mime_type IN (");
            where.append(criteria.getMimeTypes().stream().map(c -> "?").collect(Collectors.joining(",")));
            where.append(")");
            params.addAll(criteria.getMimeTypes());
        }

        return where.toString();
    }

    public List<String> getDocumentClasses(String appId, String tableType) {
        String schema = getSchema(appId);
        ApplicationConfig appConfig = configurationService.getApplicationConfig(appId);
        
        if (appConfig != null && appConfig.getClassifiedTables() != null 
            && appConfig.getClassifiedTables().get(tableType) != null 
            && !appConfig.getClassifiedTables().get(tableType).isEmpty()) {
            
            String table = validateIdentifier(appConfig.getClassifiedTables().get(tableType).get(0));
            String sql = "SELECT DISTINCT cd.symbolic_name FROM " + schema + table + " dv " +
                         "JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id " +
                         "WHERE cd.symbolic_name IS NOT NULL " + 
                         "ORDER BY cd.symbolic_name";
            try {
                List<String> classes = jdbcTemplate.queryForList(sql, String.class);
                if (!classes.isEmpty()) return classes;
            } catch (Exception e) {
                log.warn("Failed to fetch classes from table {}. Falling back to classdef. Error: {}", table, e.getMessage());
            }
        }
        
        // Fallback
        String sql = "SELECT DISTINCT symbolic_name FROM " + schema + "classdef ORDER BY symbolic_name";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    public List<Map<String, Object>> getClassProperties(String appId, String docClass) {
        String schema = getSchema(appId);
        String sql = "SELECT cd.column_name as propertyName, pd.datatype as dataType " +
                     "FROM " + schema + "columndefinition cd " +
                     "JOIN " + schema + "propertydefinition pd ON cd.object_id = pd.column_id " +
                     "WHERE cd.dbg_table_name = ? " +
                     "ORDER BY cd.column_name";
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, docClass);
            if (results.isEmpty()) {
                // Fallback for demo schema if tables don't properly join or have data
                return List.of(
                    Map.of("propertyName", docClass + "_Prop1", "dataType", "STRING"),
                    Map.of("propertyName", docClass + "_Prop2", "dataType", "INTEGER"),
                    Map.of("propertyName", docClass + "_Prop3", "dataType", "DATETIME")
                );
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to fetch properties for class: " + docClass, e);
            // Fallback for demo schema if tables don't properly join
            return List.of(
                Map.of("propertyName", docClass + "_Prop1", "dataType", "STRING"),
                Map.of("propertyName", docClass + "_Prop2", "dataType", "INTEGER"),
                Map.of("propertyName", docClass + "_Prop3", "dataType", "DATETIME")
            );
        }
    }

    public List<Map<String, Object>> executeReport(String endpoint, DiscoveryCriteria criteria) {
        String schema = getSchema(criteria.getAppId());
        ApplicationConfig appConfig = configurationService.getApplicationConfig(criteria.getAppId());
        if (appConfig == null || appConfig.getClassifiedTables() == null || appConfig.getClassifiedTables().get(SOURCE_KEY) == null || appConfig.getClassifiedTables().get(SOURCE_KEY).isEmpty()) {
            throw new ResourceNotFoundException("Source table configuration missing for application: " + criteria.getAppId());
        }
        String targetTable = validateIdentifier(appConfig.getClassifiedTables().get(SOURCE_KEY).get(0));
        String sourceTable = schema + targetTable;
        
        List<Object> params = new ArrayList<>();
        String where = buildWhereClause(criteria, params, "dv");
        String sql = buildReportSql(endpoint, schema, sourceTable, where, params, criteria);

        try {
            if (!sql.contains(WHERE_1_1)) {
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

    @SuppressWarnings("java:S3776")
    private String buildReportSql(String endpoint, String schema, String sourceTable, String where, List<Object> params, DiscoveryCriteria criteria) {
        String sql = "";
        final String joinClassDef = SQL_INNER_JOIN + schema + SQL_CLASSDEF_JOIN_COND;
        final String totalDocCount = "COUNT(dv.object_id) AS total_documents";
        final String totalSizeGbExpr = SQL_CAST_COALESCE_SUM + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + "), 0) / 1073741824.0 AS numeric(15, 6)) AS total_size_gb ";
        final String sumSizeBytesExpr = "COALESCE(SUM(" + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + "), 0) AS total_size_bytes, ";

        switch (endpoint) {
            case "doc-count":
                sql = SQL_SELECT_CLASS_NAME + "MIN(dv." + createdDateColumn + ") AS earliest_created, MAX(dv." + createdDateColumn + ") AS latest_created, " +
                      totalDocCount + ", " + sumSizeBytesExpr +
                      "COALESCE(MIN(" + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + "), 0) AS min_size_bytes, " +
                      "COALESCE(MAX(" + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + "), 0) AS max_size_bytes, " +
                      totalSizeGbExpr +
                      SQL_FROM + sourceTable + " dv " + joinClassDef +
                      where + SQL_AND_DV + createdDateColumn + SQL_IS_NOT_NULL +
                      "GROUP BY cd.symbolic_name ORDER BY class_name";
                break;
                
            case "doc-year-wise":
                sql = SQL_SELECT_CLASS_NAME + dialect.extractYear("dv." + createdDateColumn) + " AS creation_year, " +
                      totalDocCount + SQL_FROM + sourceTable + " dv " + joinClassDef +
                      where + SQL_AND_DV + createdDateColumn + SQL_IS_NOT_NULL +
                      "GROUP BY cd.symbolic_name, " + dialect.extractYear("dv." + createdDateColumn) + " " +
                      "ORDER BY cd.symbolic_name, creation_year";
                break;
                
            case "doc-year-month":
            case "custom-object-trend":
                String baseTable = endpoint.equals("custom-object-trend") ? (schema + "customobject") : sourceTable;
                sql = SQL_SELECT_CLASS_NAME + dialect.extractYear("dv." + createdDateColumn) + " AS creation_year, " +
                      dialect.extractMonth("dv." + createdDateColumn) + " AS creation_month, " + totalDocCount + " " +
                      SQL_FROM + baseTable + " dv " + joinClassDef +
                      where + SQL_AND_DV + createdDateColumn + SQL_IS_NOT_NULL +
                      "GROUP BY cd.symbolic_name, " + dialect.extractYear("dv." + createdDateColumn) + ", " + dialect.extractMonth("dv." + createdDateColumn) + " " +
                      "ORDER BY cd.symbolic_name, creation_year, creation_month";
                break;
                
            case "doc-mime":
                sql = "SELECT COALESCE(dv.mime_type, 'No MIME Type') AS mime_type, COUNT(*) AS doc_count" + SQL_FROM + sourceTable + " dv " +
                      SQL_LEFT_JOIN + schema + SQL_CLASSDEF_JOIN_COND +
                      where + " GROUP BY COALESCE(dv.mime_type, 'No MIME Type') ORDER BY doc_count DESC";
                break;
                
            case "annotation-total":
                sql = SQL_SELECT_CLASS_NAME + "COUNT(DISTINCT a.annotated_id) AS total_documents_with_annotations, " +
                      "COUNT(a.object_id) AS total_annotations" + SQL_FROM + schema + "annotation a " +
                      SQL_INNER_JOIN + sourceTable + " dv ON a.annotated_id = dv.object_id " + joinClassDef +
                      where + " GROUP BY cd.symbolic_name ORDER BY total_annotations DESC";
                break;

            case "annotation-mime":
                sql = "SELECT COALESCE(dv.mime_type, 'No MIME Type') AS mime_type, COUNT(DISTINCT a.annotated_id) AS total_documents_with_annotations, " +
                      "COUNT(a.object_id) AS total_annotations" + SQL_FROM + schema + "annotation a " +
                      SQL_INNER_JOIN + sourceTable + " dv ON a.annotated_id = dv.object_id " + joinClassDef +
                      where + " GROUP BY COALESCE(dv.mime_type, 'No MIME Type') ORDER BY total_annotations DESC";
                break;
                
            case "size-total":
                sql = SQL_SELECT_CLASS_NAME + totalDocCount + ", " + sumSizeBytesExpr +
                      SQL_CAST_COALESCE_SUM + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + "), 0) / 1048576.0 AS numeric(15, 2)) AS total_size_mb, " +
                      totalSizeGbExpr +
                      SQL_FROM + sourceTable + " dv " + joinClassDef +
                      where + SQL_AND_DV + createdDateColumn + SQL_IS_NOT_NULL +
                      "GROUP BY cd.symbolic_name ORDER BY class_name";
                break;
                
            case "size-bucket":
                sql = "SELECT CASE " + SQL_WHEN + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + " IS NULL THEN '0. No Content' " +
                      SQL_WHEN + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + " = 0 THEN '1. Zero Bytes' " +
                      SQL_WHEN + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + " BETWEEN 1 AND 102399 THEN '2. Under 100 KB' " +
                      SQL_WHEN + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + " BETWEEN 102400 AND 511999 THEN '3. 100 KB - 500 KB' " +
                      SQL_WHEN + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + " BETWEEN 512000 AND 1048575 THEN '4. 500 KB - 1 MB' " +
                      SQL_WHEN + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + " BETWEEN 1048576 AND 5242879 THEN '5. 1 MB - 5 MB' " +
                      SQL_WHEN + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + " BETWEEN 5242880 AND 10485759 THEN '6. 5 MB - 10 MB' " +
                      SQL_WHEN + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + " BETWEEN 10485760 AND 26214399 THEN '7. 10 MB - 25 MB' " +
                      SQL_WHEN + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + " BETWEEN 26214400 AND 52428799 THEN '8. 25 MB - 50 MB' " +
                      SQL_WHEN + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + " BETWEEN 52428800 AND 104857599 THEN '9. 50 MB - 100 MB' " +
                      "ELSE '10. Over 100 MB' END AS size_range, " +
                      totalDocCount + ", " + sumSizeBytesExpr +
                      SQL_CAST_COALESCE_SUM + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + "), 0) / 1048576.0 AS numeric(15, 6)) AS total_size_mb, " +
                      totalSizeGbExpr +
                      SQL_FROM + sourceTable + " dv " +
                      SQL_LEFT_JOIN + schema + SQL_CLASSDEF_JOIN_COND +
                      where + " GROUP BY size_range ORDER BY size_range";
                break;
                
            case "no-content":
                sql = SQL_SELECT_CLASS_NAME +
                      "SUM(CASE WHEN dv.content_size IS NULL OR " + dialect.castToNumeric(COL_DV_CONTENT_SIZE) + " = 0 THEN 1 ELSE 0 END) AS docs_without_content " +
                      SQL_FROM + sourceTable + " dv " + joinClassDef +
                      where + " GROUP BY cd.symbolic_name ORDER BY docs_without_content DESC";
                break;
                
            case "version-summary":
                sql = "WITH VersionCounts AS (SELECT version_series_id, object_class_id, COUNT(*) AS version_count" + SQL_FROM + sourceTable + " GROUP BY version_series_id, object_class_id) " +
                      SQL_SELECT_CLASS_NAME + "COUNT(vc.version_series_id) AS unique_documents, " +
                      "SUM(vc.version_count) AS total_versions, " +
                      dialect.castToNumeric("SUM(vc.version_count)") + " / NULLIF(COUNT(vc.version_series_id), 0) AS avg_versions_per_doc, " +
                      "MAX(vc.version_count) AS max_versions_single_doc " +
                      SQL_FROM + "VersionCounts vc " + SQL_INNER_JOIN + schema + "classdef cd ON vc.object_class_id = cd.object_id " +
                      "GROUP BY cd.symbolic_name ORDER BY total_versions DESC";
                break;
                
            case "property-defs":
                params.clear();
                sql = "SELECT pd.dbg_class_name AS class_name, gpd.symbolic_name AS property_name, pd.dbg_display_name AS display_name, " +
                      "CASE CAST(pd.datatype AS VARCHAR) " +
                      SQL_WHEN + "'0' THEN 'Unspecified' " + SQL_WHEN + "'1' THEN 'Binary' " + SQL_WHEN + "'2' THEN 'Boolean' " + SQL_WHEN + "'3' THEN 'DateTime' " +
                      SQL_WHEN + "'4' THEN 'Float64' " + SQL_WHEN + "'5' THEN 'ID' " + SQL_WHEN + "'6' THEN 'Integer32' " + SQL_WHEN + "'7' THEN 'Object' " + SQL_WHEN + "'8' THEN 'String' " +
                      "ELSE CONCAT('Unknown (', CAST(pd.datatype AS VARCHAR), ')') END AS type, " +
                      "gpd.max_length_string AS data_length " +
                      SQL_FROM + schema + "propertydefinition pd " +
                      SQL_INNER_JOIN + schema + "globalpropertydef gpd ON pd.global_prop_id = gpd.object_id " + WHERE_1_1;
                if (criteria.getDocumentClasses() != null && !criteria.getDocumentClasses().isEmpty()) {
                    sql += "AND pd.dbg_class_name IN (" + criteria.getDocumentClasses().stream().map(c -> "?").collect(Collectors.joining(",")) + ") ";
                    params.addAll(criteria.getDocumentClasses());
                }
                sql += "ORDER BY class_name, property_name";
                break;
                
            case "element-total":
                sql = "SELECT COUNT(*) AS total_content_elements" + SQL_FROM + schema + "content";
                break;
                
            case "element-class":
                sql = SQL_SELECT_CLASS_NAME + "COUNT(DISTINCT dv.object_id) AS total_documents, COUNT(c.doc_id) AS total_content_elements " +
                      SQL_FROM + schema + "content c " +
                      SQL_INNER_JOIN + sourceTable + " dv ON c.doc_id = dv.object_id " + joinClassDef +
                      where + " GROUP BY cd.symbolic_name ORDER BY total_content_elements DESC";
                break;
                
            case "element-properties":
               List<String> listTables = Arrays.asList("listofinteger32", "listofstring", "listofbinary", "listofboolean", "listofdatetime", "listoffloat64", "listofid");
               List<String> unionQueries = new ArrayList<>();
                String schemaName = schema.replace(".", "").toLowerCase();
                for (String table : listTables) {
                    try {
                        List<Integer> existsList = jdbcTemplate.queryForList("SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?", Integer.class, schemaName, table);
                        if (!existsList.isEmpty()) {
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
                sql = SQL_SELECT_CLASS_NAME +
                      "COALESCE(dv.major_version_number, '1') || '.' || COALESCE(dv.minor_version_number, '0') AS version_bucket, " +
                      "COUNT(dv.object_id) AS doc_count " +
                      SQL_FROM + sourceTable + " dv " + joinClassDef +
                      where + " GROUP BY cd.symbolic_name, COALESCE(dv.major_version_number, '1') || '.' || COALESCE(dv.minor_version_number, '0') " +
                      "ORDER BY cd.symbolic_name, version_bucket";
                break;
            case "retrieval-hex-blob":
                sql = "SELECT SUM(CASE WHEN LENGTH(COALESCE(retrieval_names, '')) > 0 AND LENGTH(COALESCE(retrieval_names, '')) <= 500 THEN 1 ELSE 0 END) AS RN1_Hex_Format_Count, " +
                      "SUM(CASE WHEN LENGTH(COALESCE(retrieval_names, '')) > 500 THEN 1 ELSE 0 END) AS RN1_Blob_Format_Count " +
                      SQL_FROM + sourceTable + " dv";
                break;
                
            case "component-hex-blob":
                sql = "SELECT SUM(CASE WHEN LENGTH(COALESCE(component_types, '')) > 0 AND LENGTH(COALESCE(component_types, '')) <= 500 THEN 1 ELSE 0 END) AS CT1_Hex_Format_Count " +
                      SQL_FROM + sourceTable + " dv";
                break;
                
            case "content-hex-blob":
                sql = "SELECT SUM(CASE WHEN LENGTH(COALESCE(content_info, '')) > 0 AND LENGTH(COALESCE(content_info, '')) <= 500 THEN 1 ELSE 0 END) AS CI1_Hex_Format_Count " +
                      SQL_FROM + sourceTable + " dv";
                break;
                
            default:
                sql = SQL_SELECT_CLASS_NAME + "COUNT(*) as doc_count" + SQL_FROM + sourceTable + " dv " +
                      SQL_LEFT_JOIN + schema + SQL_CLASSDEF_JOIN_COND +
                      where + " GROUP BY cd.symbolic_name";
                break;
        }
        return sql;
    }
}
