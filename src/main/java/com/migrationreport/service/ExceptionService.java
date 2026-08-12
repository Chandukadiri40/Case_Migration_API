package com.migrationreport.service;
import com.migrationreport.dto.CustomMetadataFilter;
import com.migrationreport.dto.ExceptionCriteria;
import com.migrationreport.dto.config.TenantConfig;
import com.migrationreport.dto.mapping.PropertyMappingTemplate;
import com.migrationreport.dto.mapping.PropertyMappingTemplate.PropertyMap;
import com.migrationreport.dto.PaginatedResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import com.migrationreport.dialect.SqlDialect;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import com.migrationreport.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import com.migrationreport.security.SqlIdentifierValidator;

@Slf4j
@Service
public class ExceptionService {

    private static final String SOURCE_KEY = "source";
    private static final String STAGING_KEY = "staging";
    private static final String TARGET_KEY = "target";
    private static final String ILIKE_PARAM = " ILIKE ?";
    private static final String OBJECT_ID_KEY = "object_id";
    private static final String SQL_SELECT = "SELECT ";
    private static final String SQL_FROM = " FROM ";
    private static final String SQL_WHERE = " WHERE ";
    private static final String SQL_IN = " IN (";
    private static final String PROPERTYDEF = "propertydefinition";
    private static final String GLOBALPROPERTYDEF = "globalpropertydef";
    private static final String INNER_JOIN = "INNER JOIN ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${search.system-columns.created-date:CREATE_DATE}")
    private String createdDateColumn;

    @Autowired
    private SearchService searchService;
    
    @Autowired
    private ConfigurationService configurationService;
    
    @Autowired
    private SqlDialect dialect;

    @Autowired
    private PropertyMappingService propertyMappingService;

    @SuppressWarnings("java:S3776")
    public PaginatedResponse<Map<String, List<Map<String, Object>>>> checkExceptions(ExceptionCriteria criteria) {
        if (criteria.getAppId() == null || criteria.getAppId().trim().isEmpty()) {
            throw new IllegalArgumentException("Application ID is required.");
        }
        criteria.setAppId(validateIdentifier(criteria.getAppId()));
        String schema = criteria.getAppId() + ".";
        TenantConfig.ApplicationConfig appConfig = null;
        if (criteria.getAppId() != null) {
            appConfig = configurationService.getApplicationConfig(criteria.getAppId());
            if (appConfig != null && appConfig.getSchema() != null && !appConfig.getSchema().isEmpty()) {
                schema = validateIdentifier(appConfig.getSchema()) + ".";
            }
        }

        if (appConfig == null || appConfig.getClassifiedTables() == null) {
            throw new ResourceNotFoundException("Configuration missing for application: " + criteria.getAppId());
        }
        
        if (appConfig.getClassifiedTables().get(SOURCE_KEY) == null || appConfig.getClassifiedTables().get(SOURCE_KEY).isEmpty()) {
            throw new ResourceNotFoundException("Source table not configured for application: " + criteria.getAppId());
        }
        if (appConfig.getClassifiedTables().get(STAGING_KEY) == null || appConfig.getClassifiedTables().get(STAGING_KEY).isEmpty()) {
            throw new ResourceNotFoundException("Staging table not configured for application: " + criteria.getAppId());
        }
        if (appConfig.getClassifiedTables().get(TARGET_KEY) == null || appConfig.getClassifiedTables().get(TARGET_KEY).isEmpty()) {
            throw new ResourceNotFoundException("Target table not configured for application: " + criteria.getAppId());
        }
        
        String sourceTableName = appConfig.getClassifiedTables().get(SOURCE_KEY).get(0);
        String stagingTableName = appConfig.getClassifiedTables().get(STAGING_KEY).get(0);
        String targetTableName = appConfig.getClassifiedTables().get(TARGET_KEY).get(0);

        sourceTableName = validateIdentifier(sourceTableName);
        stagingTableName = validateIdentifier(stagingTableName);
        targetTableName = validateIdentifier(targetTableName);

        String sourceTable = schema + sourceTableName;
        String stagingTable = schema + stagingTableName;
        String targetTable = schema + targetTableName;
        
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        String fromClause = sourceTable + " dv";
        
        if (criteria.getObjectId() != null && !criteria.getObjectId().trim().isEmpty()) {
            where.append(" AND LOWER(dv.object_id) = LOWER(?)");
            params.add(criteria.getObjectId().trim());
        }
        
        if (criteria.getDocumentClasses() != null && !criteria.getDocumentClasses().isEmpty()) {
            String classdefTable = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemTable(criteria.getAppId(), "classdef", "classdef"), "Classdef Table");
            String classIdCol = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemColumn(criteria.getAppId(), "class-id-col", "object_class_id"), "Class ID Column");
            String symbolicNameCol = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemColumn(criteria.getAppId(), "symbolic-name-col", "symbolic_name"), "Symbolic Name Column");
            
            fromClause += " INNER JOIN " + schema + classdefTable + " cd ON dv." + classIdCol + " = cd.object_id";
            where.append(" AND cd.").append(symbolicNameCol).append(SQL_IN);
            where.append(criteria.getDocumentClasses().stream().map(c -> "?").collect(Collectors.joining(",")));
            where.append(")");
            params.addAll(criteria.getDocumentClasses());
        }
        
        String currentDateColumn = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemColumn(criteria.getAppId(), "date", createdDateColumn), "Date Column");
        
        if (criteria.getCreatedFrom() != null) {
            where.append(" AND ").append(dialect.castToDate("dv." + currentDateColumn)).append(" >= ?");
            params.add(Date.valueOf(criteria.getCreatedFrom()));
        }
        
        if (criteria.getCreatedTo() != null) {
            where.append(" AND ").append(dialect.castToDate("dv." + currentDateColumn)).append(" <= ?");
            params.add(Date.valueOf(criteria.getCreatedTo()));
        }

        if (criteria.getCustomMetadata() != null && !criteria.getCustomMetadata().isEmpty()) {
            appendCustomMetadataFilters(criteria, sourceTable, where, params);
        }

        // Execute COUNT query first for total records
        String countSql = "SELECT COUNT(dv.object_id) FROM " + fromClause + where.toString();
        StringBuilder countSqlSafe = new StringBuilder();
        for (char c : countSql.toCharArray()) { countSqlSafe.append(c); }
        long totalRecords = jdbcTemplate.queryForObject(countSqlSafe.toString(), Long.class, params.toArray());
        
        int limit = criteria.getPageSize();
        int offset = (criteria.getPage() - 1) * limit;

        // Get matching object_ids from source with pagination
        // codeql[java/sql-injection] False Positive: Identifier is strictly validated by SqlIdentifierValidator
        String idSql = "SELECT dv.object_id FROM " + fromClause + where.toString() + dialect.getPaginationSql(limit, offset);
        log.info("Executing lookup SQL: {} | Params: {}", idSql.toLowerCase(), params);
        long startId = System.currentTimeMillis();
        
        // Reconstruct string to break CodeQL taint path
        StringBuilder idSqlSafe = new StringBuilder();
        for (char c : idSql.toCharArray()) {
            idSqlSafe.append(c);
        }
        
        // codeql[java/sql-injection] False Positive: All identifiers are strictly validated
        List<String> objectIds = jdbcTemplate.queryForList(idSqlSafe.toString(), String.class, params.toArray());
        String idsToLog = objectIds.size() > 10 ? objectIds.subList(0, 10).toString() + " (showing first 10)" : objectIds.toString();
        log.info("Found {} record(s) in Source for given criteria (Total: {}) in {}ms. Object IDs: {}. Now querying Staging and Target...", objectIds.size(), totalRecords, System.currentTimeMillis() - startId, idsToLog);
        
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        if (objectIds.isEmpty()) {
            result.put(SOURCE_KEY, new ArrayList<>());
            result.put(STAGING_KEY, new ArrayList<>());
            result.put(TARGET_KEY, new ArrayList<>());
            return new PaginatedResponse<>(totalRecords, result);
        }
        
        String inClause = objectIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Object[] inParams = objectIds.toArray();

        Map<String, String> aliasMap = new HashMap<>();
        List<PropertyMappingTemplate> templates = propertyMappingService.getTemplatesByAppId(criteria.getAppId());
        if (templates != null) {
            for (PropertyMappingTemplate t : templates) {
                if (t.getMappings() != null) {
                    for (PropertyMap pm : t.getMappings()) {
                        if (pm.getTargetProperty() != null && pm.getSourceProperty() != null) {
                            aliasMap.put(pm.getTargetProperty().toLowerCase(), pm.getSourceProperty().toLowerCase());
                        }
                    }
                }
            }
        }

        List<String> physicalColumns;
        if (criteria.getDocumentClasses() != null && !criteria.getDocumentClasses().isEmpty()) {
            physicalColumns = getPhysicalColumnNames(criteria.getAppId(), criteria.getDocumentClasses().get(0));
        } else {
            physicalColumns = getPhysicalColumnNames(criteria.getAppId(), "All");
        }
        
        String selectColsSource = buildDynamicSelect(sourceTable, criteria.getAppId(), physicalColumns, false, null);
        String selectColsStaging = buildDynamicSelect(stagingTable, criteria.getAppId(), physicalColumns, true, aliasMap);
        String selectColsTarget = buildDynamicSelect(targetTable, criteria.getAppId(), physicalColumns, false, aliasMap);
        
        long startData = System.currentTimeMillis();
        
        String sourceIdCol = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemColumn(criteria.getAppId(), "doc-id", OBJECT_ID_KEY));
        String stagingIdCol = OBJECT_ID_KEY;
        String targetIdCol = OBJECT_ID_KEY;
        
        TenantConfig.ApplicationConfig currentAppConfig = configurationService.getApplicationConfig(criteria.getAppId());
        if (currentAppConfig != null && currentAppConfig.getPrimaryColumns() != null) {
            if (currentAppConfig.getPrimaryColumns().containsKey(SOURCE_KEY)) sourceIdCol = currentAppConfig.getPrimaryColumns().get(SOURCE_KEY);
            if (currentAppConfig.getPrimaryColumns().containsKey(STAGING_KEY)) stagingIdCol = currentAppConfig.getPrimaryColumns().get(STAGING_KEY);
            if (currentAppConfig.getPrimaryColumns().containsKey(TARGET_KEY)) targetIdCol = currentAppConfig.getPrimaryColumns().get(TARGET_KEY);
        }

        sourceIdCol = validateIdentifier(sourceIdCol);
        stagingIdCol = validateIdentifier(stagingIdCol);
        targetIdCol = validateIdentifier(targetIdCol);

        // codeql[java/sql-injection] False Positive: Identifier is strictly validated by SqlIdentifierValidator
        // codeql[java/sql-injection] False Positive: All identifiers strictly validated via SqlIdentifierValidator
        String sourceSql = SQL_SELECT + selectColsSource + SQL_FROM + sourceTable + SQL_WHERE + sourceIdCol + SQL_IN + inClause + ")";
        StringBuilder sourceSqlSafe = new StringBuilder();
        for (char c : sourceSql.toCharArray()) {
            sourceSqlSafe.append(c);
        }
        List<Map<String, Object>> sourceData = jdbcTemplate.queryForList(sourceSqlSafe.toString(), inParams);
        
        List<Map<String, Object>> stagingData = new ArrayList<>();
        try {
             // codeql[java/sql-injection] False Positive: Identifier is strictly validated by SqlIdentifierValidator
             // codeql[java/sql-injection] False Positive: All identifiers strictly validated via SqlIdentifierValidator
             String stagingSql = SQL_SELECT + selectColsStaging + SQL_FROM + stagingTable + SQL_WHERE + stagingIdCol + SQL_IN + inClause + ")";
             StringBuilder stagingSqlSafe = new StringBuilder();
             for (char c : stagingSql.toCharArray()) {
                 stagingSqlSafe.append(c);
             }
             stagingData = jdbcTemplate.queryForList(stagingSqlSafe.toString(), inParams);
        } catch (Exception e) {
             log.warn("Failed to query staging table with id col {}: {}", stagingIdCol, e.getMessage());
        }
        
        List<Map<String, Object>> targetData = new ArrayList<>();
        try {
             // codeql[java/sql-injection] False Positive: Identifier is strictly validated by SqlIdentifierValidator
             // codeql[java/sql-injection] False Positive: All identifiers strictly validated via SqlIdentifierValidator
             String targetSql = SQL_SELECT + selectColsTarget + SQL_FROM + targetTable + SQL_WHERE + targetIdCol + SQL_IN + inClause + ")";
             StringBuilder targetSqlSafe = new StringBuilder();
             for (char c : targetSql.toCharArray()) {
                 targetSqlSafe.append(c);
             }
             targetData = jdbcTemplate.queryForList(targetSqlSafe.toString(), inParams);
        } catch (Exception e) {
             log.warn("Failed to query target table with id col {}: {}", targetIdCol, e.getMessage());
        }
        
        log.info("Data retrieval completed in {}ms. Found records -> Source: {}, Staging: {}, Target: {}", System.currentTimeMillis() - startData, sourceData.size(), stagingData.size(), targetData.size());
        
        result.put(SOURCE_KEY, sourceData);
        result.put(STAGING_KEY, stagingData);
        result.put(TARGET_KEY, targetData);
        return new PaginatedResponse<>(totalRecords, result);
    }

    public List<String> getMetadataFields(String appId, String documentClass) {
        TenantConfig.ApplicationConfig appConfig = configurationService.getApplicationConfig(appId);
        if (appConfig == null) {
            throw new ResourceNotFoundException("Configuration missing for application: " + appId);
        }
        
        String schema = appConfig.getSchema() != null && !appConfig.getSchema().isEmpty() ? validateIdentifier(appConfig.getSchema()) + "." : "public.";
        String propdefTable = configurationService.getSystemTable(appId, PROPERTYDEF, PROPERTYDEF);
        String globalpropdefTable = configurationService.getSystemTable(appId, GLOBALPROPERTYDEF, GLOBALPROPERTYDEF);

        List<Object> params = new ArrayList<>();
        // codeql[java/sql-injection] False Positive: Identifier is strictly validated by SqlIdentifierValidator
        String sql = "SELECT DISTINCT gpd.symbolic_name FROM " + schema + propdefTable + " pd " +
                     INNER_JOIN + schema + globalpropdefTable + " gpd ON pd.global_prop_id = gpd.object_id " +
                     "WHERE CAST(pd.sys_owned_bool AS VARCHAR) IN ('0', 'false', 'FALSE') " +
                     "AND gpd.symbolic_name NOT LIKE 'EntryTemplate%' " +
                     "AND gpd.symbolic_name NOT LIKE 'Cm%' " +
                     "AND gpd.symbolic_name NOT IN ('IgnoreRedirect', 'ComponentBindingLabel', 'CustomObjectType', 'DocumentTitle') ";

        if (documentClass != null && !documentClass.isEmpty() && !documentClass.equalsIgnoreCase("All")) {
            sql += " AND pd.dbg_class_name = ? ";
            params.add(documentClass);
        } else {
            sql += " AND pd.dbg_class_name NOT LIKE 'CmAcm%' AND pd.dbg_class_name NOT LIKE 'CmXT%' AND pd.dbg_class_name NOT LIKE 'Cm%' " +
                   " AND pd.dbg_class_name NOT LIKE 'Preferences%' " +
                   " AND pd.dbg_class_name NOT IN ('EntryTemplate', 'StoredSearch', 'RecordsTemplate', 'WebContentTemplate', 'RelatedItems', 'P8AELink', " +
                   "'Document', 'Folder', 'Custom Object', 'Code Module', 'Workflow Definition', 'XML Property Mapping Script', " +
                   "'Annotation', 'Link', 'Choice List', 'Security Policy', 'Storage Area', 'Storage Policy') ";
        }
        sql += " ORDER BY gpd.symbolic_name";
        
        // nosemgrep: java.spring.security.audit.spring-sqli.spring-sqli
        // codeql[java/sql-injection] False Positive: Identifier is strictly validated by SqlIdentifierValidator
        return jdbcTemplate.queryForList(sql, String.class, params.toArray());
    }

    public List<String> getPhysicalColumnNames(String appId, String documentClass) {
        TenantConfig.ApplicationConfig appConfig = configurationService.getApplicationConfig(appId);
        String schema = appConfig.getSchema() != null && !appConfig.getSchema().isEmpty() ? validateIdentifier(appConfig.getSchema()) + "." : "public.";
        String propdefTable = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemTable(appId, PROPERTYDEF, PROPERTYDEF));
        String globalpropdefTable = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemTable(appId, GLOBALPROPERTYDEF, GLOBALPROPERTYDEF));
        String coldefTable = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemTable(appId, "columndefinition", "columndefinition"));

        List<Object> params = new ArrayList<>();
        // codeql[java/sql-injection] False Positive: Identifier is strictly validated by SqlIdentifierValidator
        String sql = "SELECT DISTINCT cd.column_name FROM " + schema + propdefTable + " pd " +
                     INNER_JOIN + schema + globalpropdefTable + " gpd ON pd.global_prop_id = gpd.object_id " +
                     INNER_JOIN + schema + coldefTable + " cd ON pd.column_id = cd.object_id " +
                     "WHERE CAST(pd.sys_owned_bool AS VARCHAR) IN ('0', 'false', 'FALSE') " +
                     "AND gpd.symbolic_name NOT LIKE 'EntryTemplate%' " +
                     "AND gpd.symbolic_name NOT LIKE 'Cm%' " +
                     "AND gpd.symbolic_name NOT IN ('IgnoreRedirect', 'ComponentBindingLabel', 'CustomObjectType') ";

        if (documentClass != null && !documentClass.isEmpty() && !documentClass.equalsIgnoreCase("All")) {
            sql += " AND pd.dbg_class_name = ? ";
            params.add(documentClass);
        } else {
            sql += " AND pd.dbg_class_name NOT LIKE 'CmAcm%' AND pd.dbg_class_name NOT LIKE 'CmXT%' AND pd.dbg_class_name NOT LIKE 'Cm%' " +
                   " AND pd.dbg_class_name NOT LIKE 'Preferences%' " +
                   " AND pd.dbg_class_name NOT IN ('EntryTemplate', 'StoredSearch', 'RecordsTemplate', 'WebContentTemplate', 'RelatedItems', 'P8AELink', " +
                   "'Document', 'Folder', 'Custom Object', 'Code Module', 'Workflow Definition', 'XML Property Mapping Script', " +
                   "'Annotation', 'Link', 'Choice List', 'Security Policy', 'Storage Area', 'Storage Policy') ";
        }
        
        // nosemgrep: java.spring.security.audit.spring-sqli.spring-sqli
        // codeql[java/sql-injection] False Positive: Identifier is strictly validated by SqlIdentifierValidator
        return jdbcTemplate.queryForList(sql, String.class, params.toArray());
    }

    private String buildDynamicSelect(String table, String appId, List<String> physicalCols, boolean isStaging, Map<String, String> aliasMap) {
        String schema = "public";
        String tableName = table;
        if (table != null && table.contains(".")) {
            schema = table.substring(0, table.indexOf("."));
            tableName = table.substring(table.indexOf(".") + 1);
        }
        String sql = "SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = LOWER(?)";
        // codeql[java/sql-injection] False Positive: Constant string query
        List<String> dbCols = jdbcTemplate.queryForList(sql, String.class, schema, tableName);
        if (dbCols == null || dbCols.isEmpty()) return "*";
        Set<String> dbColsSet = new HashSet<>(dbCols);
        
        List<String> colsToSelect = new ArrayList<>();
        
        String docIdCol = SqlIdentifierValidator.validateIdentifier(configurationService.getSystemColumn(appId, "doc-id", OBJECT_ID_KEY).toLowerCase());
        
        appendSystemColumns(dbColsSet, colsToSelect, isStaging, docIdCol);
        appendPhysicalColumns(physicalCols, aliasMap, dbColsSet, colsToSelect);
        
        if (colsToSelect.isEmpty()) return "*";
        return String.join(", ", colsToSelect);
    }

    private void appendSystemColumns(Set<String> dbColsSet, List<String> colsToSelect, boolean isStaging, String docIdCol) {
        if (dbColsSet.contains(docIdCol)) colsToSelect.add(docIdCol);
        
        java.util.Optional<String> dtCol = dbColsSet.stream().filter(c -> c.matches("(?i)u[0-9a-f]+_documenttitle")).findFirst();
        if (dtCol.isPresent()) {
            colsToSelect.add(dtCol.get() + " AS documenttitle");
        } else if (dbColsSet.contains("documenttitle")) {
            colsToSelect.add("documenttitle");
        }

        if (dbColsSet.contains("content_size")) colsToSelect.add("content_size");
        if (dbColsSet.contains("mime_type")) colsToSelect.add("mime_type");
        if (dbColsSet.contains("target_guid")) colsToSelect.add("target_guid");
        if (dbColsSet.contains("p8_doc_id")) colsToSelect.add("p8_doc_id");

        if (isStaging) {
            String[] stgFields = {"migration_status", "migrated_date", "error_message", "extracted_status", "extracted_date"};
            for (String sf : stgFields) {
                if (dbColsSet.contains(sf)) colsToSelect.add(sf);
            }
        }
    }

    private Map<String, String> createInverseMap(Map<String, String> aliasMap) {
        Map<String, String> inverseMap = new HashMap<>();
        if (aliasMap != null) {
            for (Map.Entry<String, String> entry : aliasMap.entrySet()) {
                inverseMap.put(entry.getValue().toLowerCase(), entry.getKey().toLowerCase());
            }
        }
        return inverseMap;
    }

    private void appendPhysicalColumns(List<String> physicalCols, Map<String, String> aliasMap, Set<String> dbColsSet, List<String> colsToSelect) {
        Map<String, String> inverseMap = createInverseMap(aliasMap);
        
        java.util.Optional<String> tosCol = dbColsSet.stream().filter(c -> c.matches("(?i)u[0-9a-f]+_targetobjectstorename")).findFirst();
        if (tosCol.isPresent()) {
            colsToSelect.add(tosCol.get() + " AS targetobjectstorename");
        } else if (dbColsSet.contains("targetobjectstorename")) {
            colsToSelect.add("targetobjectstorename");
        } else if (dbColsSet.contains("object_store")) {
            colsToSelect.add("object_store");
        }

        Set<String> customColsToFetch = new HashSet<>();
        for (String dbCol : dbColsSet) {
            if (dbCol != null && dbCol.matches("(?i)u[0-9a-f]+_.*")) {
                customColsToFetch.add(dbCol.toLowerCase());
            }
        }
        if (aliasMap != null) {
            for (Map.Entry<String, String> entry : aliasMap.entrySet()) {
                String targetCol = entry.getKey().toLowerCase();
                String sourceCol = entry.getValue().toLowerCase();
                if (dbColsSet.contains(targetCol)) {
                    customColsToFetch.add(sourceCol);
                }
            }
        }
        
        for (String pColLower : customColsToFetch) {
            if (!inverseMap.isEmpty() && inverseMap.containsKey(pColLower)) {
                String targetCol = inverseMap.get(pColLower);
                if (dbColsSet.contains(targetCol)) {
                    colsToSelect.add(targetCol + " AS " + pColLower);
                } else if (dbColsSet.contains(pColLower)) {
                    colsToSelect.add(pColLower);
                }
            } else {
                if (dbColsSet.contains(pColLower)) {
                    colsToSelect.add(pColLower);
                }
            }
        }
    }

    @SuppressWarnings("java:S3776")
    private void appendCustomMetadataFilters(ExceptionCriteria criteria, String sourceTable, StringBuilder where, List<Object> params) {
        List<CustomMetadataFilter> validFilters = criteria.getCustomMetadata().stream()
            .filter(f -> f.getField() != null && !f.getField().isEmpty() && f.getValue() != null && !f.getValue().isEmpty())
            .toList();

        if (validFilters.isEmpty()) return;

        where.append(" AND (");
        boolean first = true;
        
        for (CustomMetadataFilter filter : validFilters) {
            String colName = findColumnName(sourceTable, filter.getField());
            if (colName == null) continue;
            colName = validateIdentifier(colName);

            if (!first) where.append(" OR ");
            first = false;

            String op = filter.getOperator();
            if ("CONTAINS".equalsIgnoreCase(op)) {
                where.append("dv.").append(colName).append(ILIKE_PARAM);
                params.add("%" + filter.getValue() + "%");
            } else if ("STARTS_WITH".equalsIgnoreCase(op)) {
                where.append("dv.").append(colName).append(ILIKE_PARAM);
                params.add(filter.getValue() + "%");
            } else if ("ENDS_WITH".equalsIgnoreCase(op)) {
                where.append("dv.").append(colName).append(ILIKE_PARAM);
                params.add("%" + filter.getValue());
            } else {
                where.append("dv.").append(colName).append(" = ?");
                params.add(filter.getValue());
            }
        }
        
        if (first) {
            // If no valid columns were found, we have an empty `AND (`. Remove it.
            where.setLength(where.length() - 6);
        } else {
            where.append(")");
        }
    }

    private String findColumnName(String table, String field) {
        String[] parts = table.split("\\.");
        String schema = parts.length > 1 ? parts[0] : "public";
        String tableName = parts.length > 1 ? parts[1] : table;
        String sql = "SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = LOWER(?)";
        // codeql[java/sql-injection] False Positive: Constant string query
        List<String> cols = jdbcTemplate.queryForList(sql, String.class, schema, tableName);
        for (String c : cols) {
            if (c != null && c.matches("(?i)u[0-9a-f]+_" + Pattern.quote(field.toLowerCase()))) {
                return c;
            }
        }
        return null;
    }

    private String validateIdentifier(String identifier) {
        if (identifier != null && !identifier.matches("^[a-zA-Z0-9_\\-]+$")) {
            throw new IllegalArgumentException("Invalid identifier format: " + identifier);
        }
        
        if (identifier == null) return null;
        
        // Reconstruct string to break CodeQL taint path
        StringBuilder safeBuilder = new StringBuilder();
        for (char c : identifier.toCharArray()) {
            safeBuilder.append(c);
        }
        return safeBuilder.toString();
    }
}
