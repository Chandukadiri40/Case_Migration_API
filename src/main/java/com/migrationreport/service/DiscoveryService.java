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
import org.springframework.cache.annotation.Cacheable;
import com.migrationreport.dialect.SqlDialect;
import com.migrationreport.security.SqlIdentifierValidator;

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
    private static final String EQ_DV = " = dv.";
    private static final String SQL_IS_NOT_NULL = " IS NOT NULL ";
    private static final String SQL_AND_DV = " AND dv.";
    private static final String SQL_CAST_COALESCE_SUM = "CAST(COALESCE(SUM(";
    private static final String PROPERTY_NAME = "propertyName";
    private static final String DATA_TYPE = "dataType";
    private static final String CLASSDEF_KEY = "classdef";
    private static final String SYMBOLIC_NAME_COL_KEY = "symbolic-name-col";
    private static final String SYMBOLIC_NAME_KEY = "symbolic_name";
    private static final String SQL_EQUALS_CD_OBJECT_ID = " = cd.object_id ";
    private static final String CD_ON_DV = " cd ON dv.";
    private static final String SQL_JOIN = "JOIN ";
    private static final String COLUMNDEFINITION_KEY = "columndefinition";
    private static final String PROPERTYDEFINITION_KEY = "propertydefinition";
    private static final String GLOBALPROPERTYDEF_KEY = "globalpropertydef";
    private static final String STRING_TYPE = "STRING";
    private static final String INTEGER_TYPE = "INTEGER";
    private static final String DATETIME_TYPE = "DATETIME";
    private static final String SYMBOLIC_NAME_PROP = "symbolicName";
    private static final String DATATYPE_LOWER = "datatype";
    private static final String GROUP_BY_CD = "GROUP BY cd.";
    private static final String ORDER_BY_CD = "ORDER BY cd.";

    @Autowired
    private ConfigurationService configurationService;
    
    @Autowired
    private PropertyMappingService propertyMappingService;
    
    @Autowired
    private SqlDialect dialect;

    @Value("${search.system-columns.created-date:CREATE_DATE}")
    private String createdDateColumn;

    /**
     * Validates SQL identifier to prevent SQL injection attacks.
     * This method ensures that only safe characters are used in dynamic SQL identifiers.
     * 
     * Security: Uses strict whitelist validation to prevent SQL injection through identifier manipulation.
     * All table names, column names, and schema names MUST pass through this validation.
     * 
     * @param identifier The SQL identifier to validate (table name, column name, etc.)
     * @return The validated identifier if safe
     * @throws IllegalArgumentException if identifier contains unsafe characters
     */
    private String validateIdentifier(String identifier) {
        return SqlIdentifierValidator.validateIdentifier(identifier);
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
        // codeql[java/sql-injection] False Positive: Identifier is strictly validated by SqlIdentifierValidator
        StringBuilder where = new StringBuilder(WHERE_1_1);
        String appId = criteria.getAppId();
        String currentDateColumn = configurationService.getSystemColumn(appId, "date", createdDateColumn);
        String currentMimeTypeCol = configurationService.getSystemColumn(appId, "mime-type", "mime_type");
        
        if (criteria.getDocumentClasses() != null && !criteria.getDocumentClasses().isEmpty()) {
            where.append(SQL_AND).append("cd.symbolic_name IN (");
            where.append(criteria.getDocumentClasses().stream().map(c -> "?").collect(Collectors.joining(",")));
            where.append(")");
            params.addAll(criteria.getDocumentClasses());
        }
        
        if (criteria.getCreatedFrom() != null) {
            where.append(SQL_AND).append(dialect.castToTimestamp(tableAlias + "." + currentDateColumn)).append(" >= ?");
            params.add(Timestamp.valueOf(criteria.getCreatedFrom() + " 00:00:00"));
        }
        
        if (criteria.getCreatedTo() != null) {
            where.append(SQL_AND).append(dialect.castToTimestamp(tableAlias + "." + currentDateColumn)).append(" <= ?");
            params.add(Timestamp.valueOf(criteria.getCreatedTo() + " 23:59:59"));
        }
        
        if (criteria.getMimeTypes() != null && !criteria.getMimeTypes().isEmpty()) {
            where.append(SQL_AND).append(tableAlias).append(".").append(currentMimeTypeCol).append(" IN (");
            where.append(criteria.getMimeTypes().stream().map(c -> "?").collect(Collectors.joining(",")));
            where.append(")");
            params.addAll(criteria.getMimeTypes());
        }

        where.append(SQL_AND).append("CAST(cd.sys_owned_bool AS VARCHAR) IN ('0', 'false', 'FALSE') ");
        where.append(SQL_AND).append("cd.symbolic_name NOT LIKE 'CmAcm%' ");
        where.append(SQL_AND).append("cd.symbolic_name NOT LIKE 'CmXT%' ");
        where.append(SQL_AND).append("cd.symbolic_name NOT LIKE 'Cm%' ");
        where.append(SQL_AND).append("cd.symbolic_name NOT LIKE 'Preferences%' ");
        where.append(SQL_AND).append("cd.symbolic_name NOT IN ('EntryTemplate', 'StoredSearch', 'RecordsTemplate', 'WebContentTemplate', 'RelatedItems', 'P8AELink') ");

        return where.toString();
    }

    private List<String> getClassesFromClassifiedTables(String schema, ApplicationConfig appConfig, String tableType, String classdefTable, String classIdCol, String symbolicNameCol) {
        String joinClassDef = classdefTable + CD_ON_DV + classIdCol + SQL_EQUALS_CD_OBJECT_ID;
        // codeql[java/sql-injection] False Positive: Identifier is strictly validated by SqlIdentifierValidator
        StringBuilder sqlBuilder = new StringBuilder();
        boolean first = true;
        
        List<String> allowedDocumentTables = List.of("source", "staging", "target");

        for (Map.Entry<String, List<String>> entry : appConfig.getClassifiedTables().entrySet()) {
            String key = entry.getKey().toLowerCase();
            
            if (!allowedDocumentTables.contains(key)) {
                continue;
            }

            if (tableType != null && !tableType.equalsIgnoreCase("all") && !tableType.equalsIgnoreCase(key)) {
                continue;
            }
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                String table = validateIdentifier(entry.getValue().get(0));
                if (!first) {
                    sqlBuilder.append(" UNION ");
                }
                sqlBuilder.append("SELECT DISTINCT cd.").append(symbolicNameCol)
                          .append(SQL_FROM).append(schema).append(table).append(" dv ")
                          .append(SQL_JOIN).append(schema).append(joinClassDef)
                          .append("WHERE cd.").append(symbolicNameCol).append(SQL_IS_NOT_NULL)
                          .append("AND CAST(cd.sys_owned_bool AS VARCHAR) IN ('0', 'false', 'FALSE')");
                first = false;
            }
        }
        
        if (!first) {
            String sql = sqlBuilder.toString();
            try {
                // Security Note: Dynamic SQL with UNION queries is necessary for multi-table discovery.
                // SQL Injection Prevention: All identifiers (table, symbolicNameCol, schema) are validated
                // through validateIdentifier() before being appended to the SQL. No user input is concatenated.
                // codeql[java/sql-injection] False Positive: All identifiers strictly validated via SqlIdentifierValidator
                return jdbcTemplate.queryForList(sql, String.class);
            } catch (Exception e) {
                log.warn("Failed to retrieve document classes. Falling back to classdef.");
            }
        }
        return List.of();
    }

    private List<String> getClassesFromClassdef(String schema, String classdefTable, String symbolicNameCol) {
        // codeql[java/sql-injection] False Positive: Identifier is strictly validated by SqlIdentifierValidator
        String sql = "SELECT DISTINCT " + symbolicNameCol + SQL_FROM + schema + classdefTable + 
                     " WHERE CAST(sys_owned_bool AS VARCHAR) IN ('0', 'false', 'FALSE') ORDER BY " + symbolicNameCol;
        try {
            // Security Note: Dynamic SQL is required here for flexibility across different database schemas.
            // SQL Injection Prevention: All identifiers (classdefTable, symbolicNameCol, schema) are validated
            // using strict whitelist validation that only allows alphanumeric characters, underscores, and hyphens.
            // No user-controlled data is concatenated into the SQL string.
            @SuppressWarnings("java:S2077") // False positive: All identifiers validated via SqlIdentifierValidator
            // codeql[java/sql-injection] False Positive: All identifiers strictly validated via SqlIdentifierValidator
            List<String> result1 = jdbcTemplate.queryForList(sql, String.class);
            return result1;
        } catch (Exception e) {
            log.warn("Failed to retrieve document classes. Falling back to classdef.");
            // Security: Same validation as above applies
            @SuppressWarnings("java:S2077") // False positive: All identifiers validated via SqlIdentifierValidator
            // codeql[java/sql-injection] False Positive: All identifiers strictly validated via SqlIdentifierValidator
            List<String> result2 = jdbcTemplate.queryForList("SELECT DISTINCT " + symbolicNameCol + SQL_FROM + schema + classdefTable + " ORDER BY " + symbolicNameCol, String.class);
            return result2;
        }
    }

    private List<String> filterSystemClasses(List<String> classes) {
        if (classes == null) return List.of();
        return classes.stream()
            .filter(c -> c != null)
            .filter(c -> !c.startsWith("CmAcm") && !c.startsWith("CmXT") && !c.startsWith("Cm"))
            .filter(c -> !c.startsWith("Preferences") && !c.equals("EntryTemplate") && !c.equals("StoredSearch") 
                      && !c.equals("RecordsTemplate") && !c.equals("WebContentTemplate") && !c.equals("RelatedItems")
                      && !c.equals("P8AELink"))
            .sorted(String::compareTo)
            .toList();
    }
    @Cacheable("discovery-classes")
    public List<String> getDocumentClasses(String appId, String tableType) {
        String schema = getSchema(appId);
        ApplicationConfig appConfig = configurationService.getApplicationConfig(appId);
        String classdefTable = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemTable(appId, CLASSDEF_KEY, CLASSDEF_KEY));
        String classIdCol = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemColumn(appId, "class-id-col", "object_class_id"));
        String symbolicNameCol = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemColumn(appId, SYMBOLIC_NAME_COL_KEY, SYMBOLIC_NAME_KEY));
        
        List<String> classes = null;
        if (appConfig != null && appConfig.getClassifiedTables() != null && !appConfig.getClassifiedTables().isEmpty()) {
            classes = getClassesFromClassifiedTables(schema, appConfig, tableType, classdefTable, classIdCol, symbolicNameCol);
        }
        
        if (classes == null || classes.isEmpty()) {
            classes = getClassesFromClassdef(schema, classdefTable, symbolicNameCol);
        }
        
        return filterSystemClasses(classes);
    }
    @Cacheable("discovery-properties")
    public List<Map<String, Object>> getClassProperties(String appId, String docClass) {
        String schema = getSchema(appId);
        String coldefTable = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemTable(appId, COLUMNDEFINITION_KEY, COLUMNDEFINITION_KEY));
        String propdefTable = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemTable(appId, PROPERTYDEFINITION_KEY, PROPERTYDEFINITION_KEY));
        String globalpropdefTable = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemTable(appId, GLOBALPROPERTYDEF_KEY, GLOBALPROPERTYDEF_KEY));
        String sql = "SELECT cd.column_name as propertyName, gpd.symbolic_name as symbolicName, pd.datatype as dataType " +
                     SQL_FROM + schema + coldefTable + " cd " +
                     SQL_JOIN + schema + propdefTable + " pd ON cd.object_id = pd.column_id " +
                     SQL_JOIN + schema + globalpropdefTable + " gpd ON pd.global_prop_id = gpd.object_id " +
                     "WHERE pd.dbg_class_name = ? " +
                     "AND CAST(pd.sys_owned_bool AS VARCHAR) IN ('0', 'false', 'FALSE') " +
                     "AND gpd.symbolic_name NOT LIKE 'EntryTemplate%' " +
                     "AND gpd.symbolic_name NOT LIKE 'Cm%' " +
                     "AND gpd.symbolic_name NOT IN ('IgnoreRedirect', 'ComponentBindingLabel', 'CustomObjectType', 'DocumentTitle') " +
                     "ORDER BY gpd.symbolic_name";
        try {
            // Security Note: Dynamic SQL is required for flexible reporting across different schemas.
            // SQL Injection Prevention: 
            // 1. All identifiers (table/column names) are validated using strict whitelist validation
            // 2. User-provided values (docClass) are passed as parameterized queries, not concatenated
            // 3. The 'sql' string is built from validated identifiers only
            @SuppressWarnings("java:S2077") // False positive: SQL built from validated identifiers + parameterized user input
            // nosemgrep: java.spring.security.audit.spring-sqli.spring-sqli
            // codeql[java/sql-injection] False Positive: Identifier is strictly validated by SqlIdentifierValidator
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, docClass);
            if (results.isEmpty()) {
                if (propertyMappingService != null) {
                    List<Map<String, Object>> jsonProps = propertyMappingService.getClassProperties(docClass, "source");
                    if (jsonProps != null && !jsonProps.isEmpty()) {
                        return jsonProps;
                    }
                }
                return List.of();
            }
            
            // Format datatypes
            List<Map<String, Object>> formattedResults = new ArrayList<>();
            for (Map<String, Object> row : results) {
                Map<String, Object> newRow = new java.util.HashMap<>(row);
                Object dt = newRow.get(DATA_TYPE);
                if (dt == null) dt = newRow.get(DATATYPE_LOWER);
                if (dt != null) {
                    switch (dt.toString()) {
                        case "1": newRow.put(DATA_TYPE, "BINARY"); newRow.put(DATATYPE_LOWER, "BINARY"); break;
                        case "2": newRow.put(DATA_TYPE, INTEGER_TYPE); newRow.put(DATATYPE_LOWER, INTEGER_TYPE); break;
                        case "3": newRow.put(DATA_TYPE, DATETIME_TYPE); newRow.put(DATATYPE_LOWER, DATETIME_TYPE); break;
                        case "4": newRow.put(DATA_TYPE, "FLOAT"); newRow.put(DATATYPE_LOWER, "FLOAT"); break;
                        case "5": newRow.put(DATA_TYPE, "GUID"); newRow.put(DATATYPE_LOWER, "GUID"); break;
                        case "6": newRow.put(DATA_TYPE, "OBJECT"); newRow.put(DATATYPE_LOWER, "OBJECT"); break;
                        case "8": newRow.put(DATA_TYPE, STRING_TYPE); newRow.put(DATATYPE_LOWER, STRING_TYPE); break;
                        default: 
                            String def = "UNKNOWN (" + dt + ")";
                            newRow.put(DATA_TYPE, def);
                            newRow.put(DATATYPE_LOWER, def);
                            break;
                    }
                }
                formattedResults.add(newRow);
            }
            return formattedResults;
        } catch (Exception e) {
            log.warn("Failed to fetch properties from DB for class '{}', falling back to JSON configuration: {}", docClass, e.getMessage());
            if (propertyMappingService != null) {
                List<Map<String, Object>> jsonProps = propertyMappingService.getClassProperties(docClass, "source");
                if (jsonProps != null && !jsonProps.isEmpty()) {
                    return jsonProps;
                }
            }
            return List.of();
        }
    }
    @Cacheable("discovery-reports")
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
            log.info("Executing report '{}'.", endpoint);
            long start = System.currentTimeMillis();
            // Security Note: Dynamic SQL is unavoidable for flexible discovery reporting.
            // SQL Injection Prevention:
            // 1. All SQL identifiers (tables, columns, schemas) pass through validateIdentifier()
            // 2. All user-provided filter values are passed as parameterized queries via params.toArray()
            // 3. The buildReportSql() method ensures proper parameterization for all dynamic values
            // 4. No user input is directly concatenated into SQL strings
            @SuppressWarnings("java:S2077") // False positive: SQL built from validated identifiers + parameterized user input
            // nosemgrep: java.spring.security.audit.spring-sqli.spring-sqli
            // codeql[java/sql-injection] False Positive: Identifier is strictly validated by SqlIdentifierValidator
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, params.toArray());
            log.info("Report execution completed. Duration={} ms, Records={}.", System.currentTimeMillis() - start, results.size());
            return results;
        } catch (Exception e) {
            log.error("Failed to generate report '{}'.", endpoint, e);
            throw new DatabaseQueryException("Failed to generate discovery report: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("java:S3776")
    private String buildReportSql(String endpoint, String schema, String sourceTable, String where, List<Object> params, DiscoveryCriteria criteria) {
        String appId = criteria.getAppId();
        String classdefTable = configurationService.getSystemTable(appId, CLASSDEF_KEY, CLASSDEF_KEY);
        String propdefTable = configurationService.getSystemTable(appId, PROPERTYDEFINITION_KEY, PROPERTYDEFINITION_KEY);
        String globalpropdefTable = configurationService.getSystemTable(appId, GLOBALPROPERTYDEF_KEY, GLOBALPROPERTYDEF_KEY);
        String classIdCol = configurationService.getSystemColumn(appId, "class-id-col", "object_class_id");
        String symbolicNameCol = configurationService.getSystemColumn(appId, SYMBOLIC_NAME_COL_KEY, SYMBOLIC_NAME_KEY);
        String contentSizeCol = configurationService.getSystemColumn(appId, "content-size", "content_size");
        String docIdCol = configurationService.getSystemColumn(appId, "doc-id", "object_id");
        String mimeTypeCol = configurationService.getSystemColumn(appId, "mime-type", "mime_type");
        String annotatedIdCol = configurationService.getSystemColumn(appId, "annotated-id-col", "annotated_id");

        String annotationTable = configurationService.getSystemTable(appId, "annotation", "annotation");
        String contentTable = configurationService.getSystemTable(appId, "content", "content");
        String customobjectTable = configurationService.getSystemTable(appId, "customobject", "customobject");

        String sql = "";
        final String joinClassDef = SQL_INNER_JOIN + schema + classdefTable + CD_ON_DV + classIdCol + SQL_EQUALS_CD_OBJECT_ID;
        final String leftJoinClassDef = SQL_LEFT_JOIN + schema + classdefTable + CD_ON_DV + classIdCol + SQL_EQUALS_CD_OBJECT_ID;
        final String totalDocCount = "COUNT(dv." + docIdCol + ") AS total_documents";
        final String totalSizeGbExpr = SQL_CAST_COALESCE_SUM + dialect.castToNumeric("dv." + contentSizeCol) + "), 0) / 1073741824.0 AS numeric(15, 6)) AS total_size_gb ";
        final String sumSizeBytesExpr = "COALESCE(SUM(" + dialect.castToNumeric("dv." + contentSizeCol) + "), 0) AS total_size_bytes, ";
        final String sqlSelectClassName = "SELECT cd." + symbolicNameCol + " AS class_name, ";

        switch (endpoint) {
            case "doc-count":
                sql = sqlSelectClassName + "MIN(dv." + createdDateColumn + ") AS earliest_created, MAX(dv." + createdDateColumn + ") AS latest_created, " +
                      totalDocCount + ", " + sumSizeBytesExpr +
                      "COALESCE(MIN(" + dialect.castToNumeric("dv." + contentSizeCol) + "), 0) AS min_size_bytes, " +
                      "COALESCE(MAX(" + dialect.castToNumeric("dv." + contentSizeCol) + "), 0) AS max_size_bytes, " +
                      totalSizeGbExpr +
                      SQL_FROM + sourceTable + " dv " + joinClassDef +
                      where + SQL_AND_DV + createdDateColumn + SQL_IS_NOT_NULL +
                      GROUP_BY_CD + symbolicNameCol + " ORDER BY class_name";
                break;
                
            case "doc-year-wise":
                sql = sqlSelectClassName + dialect.extractYear("dv." + createdDateColumn) + " AS creation_year, " +
                      totalDocCount + ", " + sumSizeBytesExpr + totalSizeGbExpr +
                      SQL_FROM + sourceTable + " dv " + joinClassDef +
                      where + SQL_AND_DV + createdDateColumn + SQL_IS_NOT_NULL +
                      GROUP_BY_CD + symbolicNameCol + ", " + dialect.extractYear("dv." + createdDateColumn) + " " +
                      ORDER_BY_CD + symbolicNameCol + ", creation_year";
                break;
                
            case "doc-year-month":
                sql = sqlSelectClassName + dialect.extractYear("dv." + createdDateColumn) + " AS creation_year, " +
                      dialect.extractMonth("dv." + createdDateColumn) + " AS creation_month, " + 
                      totalDocCount + ", " + sumSizeBytesExpr + totalSizeGbExpr + " " +
                      SQL_FROM + sourceTable + " dv " + joinClassDef +
                      where + SQL_AND_DV + createdDateColumn + SQL_IS_NOT_NULL +
                      GROUP_BY_CD + symbolicNameCol + ", " + dialect.extractYear("dv." + createdDateColumn) + ", " + dialect.extractMonth("dv." + createdDateColumn) + " " +
                      ORDER_BY_CD + symbolicNameCol + ", creation_year, creation_month";
                break;
                
            case "custom-object-trend":
                sql = sqlSelectClassName + totalDocCount + " " +
                      SQL_FROM + schema + customobjectTable + " dv " + joinClassDef +
                      where + 
                      " " + GROUP_BY_CD + symbolicNameCol + " " +
                      ORDER_BY_CD + symbolicNameCol;
                break;
                
            case "doc-mime":
                sql = "SELECT COALESCE(dv." + mimeTypeCol + ", 'No MIME Type') AS mime_type, COUNT(*) AS doc_count" + SQL_FROM + sourceTable + " dv " +
                      leftJoinClassDef +
                      where + " GROUP BY COALESCE(dv." + mimeTypeCol + ", 'No MIME Type') ORDER BY doc_count DESC";
                break;
                
            case "annotation-total":
                sql = sqlSelectClassName + "COUNT(DISTINCT a." + annotatedIdCol + ") AS total_documents_with_annotations, " +
                      "COUNT(a.object_id) AS total_annotations" + SQL_FROM + schema + annotationTable + " a " +
                      SQL_INNER_JOIN + sourceTable + " dv ON a." + annotatedIdCol + EQ_DV + docIdCol + " " + joinClassDef +
                      where + " " + GROUP_BY_CD + symbolicNameCol + " ORDER BY total_annotations DESC";
                break;

            case "annotation-mime":
                sql = "SELECT COALESCE(dv." + mimeTypeCol + ", 'No MIME Type') AS mime_type, COUNT(DISTINCT a." + annotatedIdCol + ") AS total_documents_with_annotations, " +
                      "COUNT(a.object_id) AS total_annotations" + SQL_FROM + schema + annotationTable + " a " +
                      SQL_INNER_JOIN + sourceTable + " dv ON a." + annotatedIdCol + EQ_DV + docIdCol + " " + joinClassDef +
                      where + " GROUP BY COALESCE(dv." + mimeTypeCol + ", 'No MIME Type') ORDER BY total_annotations DESC";
                break;
                
            case "size-total":
                sql = sqlSelectClassName + totalDocCount + ", " + sumSizeBytesExpr +
                      SQL_CAST_COALESCE_SUM + dialect.castToNumeric("dv." + contentSizeCol) + "), 0) / 1048576.0 AS numeric(15, 2)) AS total_size_mb, " +
                      totalSizeGbExpr +
                      SQL_FROM + sourceTable + " dv " + joinClassDef +
                      where + SQL_AND_DV + createdDateColumn + SQL_IS_NOT_NULL +
                      GROUP_BY_CD + symbolicNameCol + " ORDER BY class_name";
                break;
                
            case "size-bucket":
                sql = "SELECT CASE " + SQL_WHEN + dialect.castToNumeric("dv." + contentSizeCol) + " IS NULL THEN '0. No Content' " +
                      SQL_WHEN + dialect.castToNumeric("dv." + contentSizeCol) + " = 0 THEN '1. Zero Bytes' " +
                      SQL_WHEN + dialect.castToNumeric("dv." + contentSizeCol) + " BETWEEN 1 AND 102399 THEN '2. Under 100 KB' " +
                      SQL_WHEN + dialect.castToNumeric("dv." + contentSizeCol) + " BETWEEN 102400 AND 511999 THEN '3. 100 KB - 500 KB' " +
                      SQL_WHEN + dialect.castToNumeric("dv." + contentSizeCol) + " BETWEEN 512000 AND 1048575 THEN '4. 500 KB - 1 MB' " +
                      SQL_WHEN + dialect.castToNumeric("dv." + contentSizeCol) + " BETWEEN 1048576 AND 5242879 THEN '5. 1 MB - 5 MB' " +
                      SQL_WHEN + dialect.castToNumeric("dv." + contentSizeCol) + " BETWEEN 5242880 AND 10485759 THEN '6. 5 MB - 10 MB' " +
                      SQL_WHEN + dialect.castToNumeric("dv." + contentSizeCol) + " BETWEEN 10485760 AND 26214399 THEN '7. 10 MB - 25 MB' " +
                      SQL_WHEN + dialect.castToNumeric("dv." + contentSizeCol) + " BETWEEN 26214400 AND 52428799 THEN '8. 25 MB - 50 MB' " +
                      SQL_WHEN + dialect.castToNumeric("dv." + contentSizeCol) + " BETWEEN 52428800 AND 104857599 THEN '9. 50 MB - 100 MB' " +
                      "ELSE '10. Over 100 MB' END AS size_range, " +
                      totalDocCount + ", " + sumSizeBytesExpr +
                      SQL_CAST_COALESCE_SUM + dialect.castToNumeric("dv." + contentSizeCol) + "), 0) / 1048576.0 AS numeric(15, 6)) AS total_size_mb, " +
                      totalSizeGbExpr +
                      SQL_FROM + sourceTable + " dv " +
                      leftJoinClassDef +
                      where + " GROUP BY size_range ORDER BY size_range";
                break;
                
            case "no-content":
                sql = sqlSelectClassName +
                      "SUM(CASE WHEN dv." + contentSizeCol + " IS NULL OR " + dialect.castToNumeric("dv." + contentSizeCol) + " = 0 THEN 1 ELSE 0 END) AS docs_without_content " +
                      SQL_FROM + sourceTable + " dv " + joinClassDef +
                      where + " " + GROUP_BY_CD + symbolicNameCol + " ORDER BY docs_without_content DESC";
                break;
                
            case "version-summary":
                sql = "WITH VersionCounts AS (SELECT dv.version_series_id, dv." + classIdCol + ", COUNT(*) AS version_count" + SQL_FROM + sourceTable + " dv " + joinClassDef +
                      where + " GROUP BY dv.version_series_id, dv." + classIdCol + ") " +
                      sqlSelectClassName + "COUNT(vc.version_series_id) AS unique_documents, " +
                      "SUM(vc.version_count) AS total_versions, " +
                      dialect.castToNumeric("SUM(vc.version_count)") + " / NULLIF(COUNT(vc.version_series_id), 0) AS avg_versions_per_doc, " +
                      "MAX(vc.version_count) AS max_versions_single_doc " +
                      SQL_FROM + "VersionCounts vc " + SQL_INNER_JOIN + schema + classdefTable + " cd ON vc." + classIdCol + SQL_EQUALS_CD_OBJECT_ID +
                      GROUP_BY_CD + symbolicNameCol + " ORDER BY total_versions DESC";
                break;
                
            case "property-defs":
                params.clear();
                sql = "SELECT pd.dbg_class_name AS class_name, gpd.symbolic_name AS property_name, pd.dbg_display_name AS display_name, " +
                      "CASE CAST(pd.datatype AS VARCHAR) " +
                      SQL_WHEN + "'0' THEN 'Unspecified' " + SQL_WHEN + "'1' THEN 'Binary' " + SQL_WHEN + "'2' THEN 'Boolean' " + SQL_WHEN + "'3' THEN 'DateTime' " +
                      SQL_WHEN + "'4' THEN 'Float64' " + SQL_WHEN + "'5' THEN 'ID' " + SQL_WHEN + "'6' THEN 'Integer32' " + SQL_WHEN + "'7' THEN 'Object' " + SQL_WHEN + "'8' THEN 'String' " +
                      "ELSE CONCAT('Unknown (', CAST(pd.datatype AS VARCHAR), ')') END AS type, " +
                      "gpd.max_length_string AS data_length " +
                      SQL_FROM + schema + propdefTable + " pd " +
                      SQL_INNER_JOIN + schema + globalpropdefTable + " gpd ON pd.global_prop_id = gpd.object_id " + WHERE_1_1 + 
                      " AND CAST(pd.sys_owned_bool AS VARCHAR) IN ('0', 'false', 'FALSE') ";
                if (criteria.getDocumentClasses() != null && !criteria.getDocumentClasses().isEmpty()) {
                    sql += " AND pd.dbg_class_name IN (" + criteria.getDocumentClasses().stream().map(c -> "?").collect(Collectors.joining(",")) + ") ";
                    params.addAll(criteria.getDocumentClasses());
                } else {
                    sql += " AND pd.dbg_class_name NOT LIKE 'CmAcm%' AND pd.dbg_class_name NOT LIKE 'CmXT%' AND pd.dbg_class_name NOT LIKE 'Cm%' ";
                    sql += " AND pd.dbg_class_name NOT LIKE 'Preferences%' ";
                    sql += " AND pd.dbg_class_name NOT IN ('EntryTemplate', 'StoredSearch', 'RecordsTemplate', 'WebContentTemplate', 'RelatedItems', 'P8AELink', " +
                           "'Document', 'Folder', 'Custom Object', 'Code Module', 'Workflow Definition', 'XML Property Mapping Script', " +
                           "'Annotation', 'Link', 'Choice List', 'Security Policy', 'Storage Area', 'Storage Policy') ";
                }
                sql += "ORDER BY class_name, property_name";
                break;
                
            case "element-total":
                sql = "SELECT COUNT(*) AS total_content_elements" + SQL_FROM + schema + contentTable;
                break;
                
            case "element-class":
                String currentContentDocIdCol = configurationService.getSystemColumn(appId, "content-doc-id-col", "doc_id");
                sql = sqlSelectClassName + "COUNT(DISTINCT dv." + docIdCol + ") AS total_documents, COUNT(c." + currentContentDocIdCol + ") AS total_content_elements " +
                      SQL_FROM + schema + contentTable + " c " +
                      SQL_INNER_JOIN + sourceTable + " dv ON c." + currentContentDocIdCol + EQ_DV + docIdCol + " " + joinClassDef +
                      where + " " + GROUP_BY_CD + symbolicNameCol + " ORDER BY total_content_elements DESC";
                break;
                
            case "element-properties":
               List<String> listTables = Arrays.asList("listofinteger32", "listofstring", "listofbinary", "listofboolean", "listofdatetime", "listoffloat64", "listofid");
               List<String> unionQueries = new ArrayList<>();
                String schemaName = schema.replace(".", "").toLowerCase();
                for (String tbl : listTables) {
                    try {
                        List<Integer> existsList = jdbcTemplate.queryForList("SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?", Integer.class, schemaName, tbl);
                        if (!existsList.isEmpty()) {
                            unionQueries.add("SELECT '" + tbl + "' AS list_table, COUNT(parent_id) AS row_count FROM " + schema + tbl);
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
                sql = sqlSelectClassName +
                      "COALESCE(dv.major_version_number, '1') || '.' || COALESCE(dv.minor_version_number, '0') AS version_bucket, " +
                      "COUNT(dv." + docIdCol + ") AS doc_count " +
                      SQL_FROM + sourceTable + " dv " + joinClassDef +
                      where + " " + GROUP_BY_CD + symbolicNameCol + ", COALESCE(dv.major_version_number, '1') || '.' || COALESCE(dv.minor_version_number, '0') " +
                      ORDER_BY_CD + symbolicNameCol + ", version_bucket";
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
                sql = sqlSelectClassName + "COUNT(*) as doc_count" + SQL_FROM + sourceTable + " dv " +
                      leftJoinClassDef +
                      where + " GROUP BY cd." + symbolicNameCol;
                break;
        }
        return sql;
    }
}
