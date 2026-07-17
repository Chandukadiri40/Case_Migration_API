package com.migrationreport.service;
import com.migrationreport.dto.CustomMetadataFilter;
import com.migrationreport.dto.ExceptionCriteria;
import com.migrationreport.dto.config.TenantConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.migrationreport.dialect.SqlDialect;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import com.migrationreport.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;

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

    @SuppressWarnings("java:S3776")
    public Map<String, List<Map<String, Object>>> checkExceptions(ExceptionCriteria criteria) {
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
            fromClause += " INNER JOIN " + schema + "classdef cd ON dv.object_class_id = cd.object_id";
            where.append(" AND cd.symbolic_name IN (");
            where.append(criteria.getDocumentClasses().stream().map(c -> "?").collect(Collectors.joining(",")));
            where.append(")");
            params.addAll(criteria.getDocumentClasses());
        }
        
        if (criteria.getCreatedFrom() != null) {
            where.append(" AND ").append(dialect.castToDate("dv." + createdDateColumn)).append(" >= ?");
            params.add(Date.valueOf(criteria.getCreatedFrom()));
        }
        
        if (criteria.getCreatedTo() != null) {
            where.append(" AND ").append(dialect.castToDate("dv." + createdDateColumn)).append(" <= ?");
            params.add(java.sql.Date.valueOf(criteria.getCreatedTo()));
        }

        if (criteria.getCustomMetadata() != null && !criteria.getCustomMetadata().isEmpty()) {
            appendCustomMetadataFilters(criteria, sourceTable, where, params);
        }

        // Get matching object_ids from source
        String idSql = "SELECT dv.object_id FROM " + fromClause + where.toString();
        log.info("[EXCEPTIONS] Executing lookup SQL: {} | Params: {}", idSql, params);
        long startId = System.currentTimeMillis();
        List<String> objectIds = jdbcTemplate.queryForList(idSql, String.class, params.toArray());
        log.info("[EXCEPTIONS] Found {} target IDs in {}ms", objectIds.size(), System.currentTimeMillis() - startId);
        
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        if (objectIds.isEmpty()) {
            result.put("source", new ArrayList<>());
            result.put("staging", new ArrayList<>());
            result.put("target", new ArrayList<>());
            return result;
        }
        
        String inClause = objectIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Object[] inParams = objectIds.toArray();
        String selectColsSource = searchService.buildSelectClauseForTable(sourceTable, null);
        String selectColsStaging = searchService.buildSelectClauseForTable(stagingTable, null);
        String selectColsTarget = searchService.buildSelectClauseForTable(targetTable, null);
        
        log.info("[EXCEPTIONS] Querying Source, Staging, Target tables for {} object IDs", objectIds.size());
        long startData = System.currentTimeMillis();
        
        String sourceIdCol = configurationService.getSystemColumn(criteria.getAppId(), "doc-id", OBJECT_ID_KEY);
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

        List<Map<String, Object>> sourceData = jdbcTemplate.queryForList(SQL_SELECT + selectColsSource + SQL_FROM + sourceTable + SQL_WHERE + sourceIdCol + SQL_IN + inClause + ")", inParams);
        
        List<Map<String, Object>> stagingData = new ArrayList<>();
        try {
             stagingData = jdbcTemplate.queryForList(SQL_SELECT + selectColsStaging + SQL_FROM + stagingTable + SQL_WHERE + stagingIdCol + SQL_IN + inClause + ")", inParams);
        } catch (Exception e) {
             log.warn("Failed to query staging table with id col {}: {}", stagingIdCol, e.getMessage());
        }
        
        List<Map<String, Object>> targetData = new ArrayList<>();
        try {
             targetData = jdbcTemplate.queryForList(SQL_SELECT + selectColsTarget + SQL_FROM + targetTable + SQL_WHERE + targetIdCol + SQL_IN + inClause + ")", inParams);
        } catch (Exception e) {
             log.warn("Failed to query target table with id col {}: {}", targetIdCol, e.getMessage());
        }
        
        log.info("[EXCEPTIONS] Data retrieval completed in {}ms. Source: {}, Staging: {}, Target: {}", System.currentTimeMillis() - startData, sourceData.size(), stagingData.size(), targetData.size());
        
        result.put("source", sourceData);
        result.put("staging", stagingData);
        result.put("target", targetData);
        return result;
    }

    public List<String> getMetadataFields(String appId) {
        TenantConfig.ApplicationConfig appConfig = configurationService.getApplicationConfig(appId);
        if (appConfig == null || appConfig.getClassifiedTables() == null || appConfig.getClassifiedTables().get(SOURCE_KEY) == null || appConfig.getClassifiedTables().get(SOURCE_KEY).isEmpty()) {
            throw new ResourceNotFoundException("Source table configuration missing for application: " + appId);
        }
        
        String schema = appConfig.getSchema() != null && !appConfig.getSchema().isEmpty() ? appConfig.getSchema() : "public";
        String tableName = appConfig.getClassifiedTables().get(SOURCE_KEY).get(0);
        String sql = "SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = LOWER(?)";
        List<String> cols = jdbcTemplate.queryForList(sql, String.class, schema, tableName);
        return cols.stream()
                   .filter(c -> c != null && c.matches("(?i)u[0-9a-f]+_.*"))
                   .map(c -> c.substring(c.indexOf('_') + 1))
                   .sorted()
                   .toList();
    }

    @SuppressWarnings("java:S3776")
    private void appendCustomMetadataFilters(ExceptionCriteria criteria, String sourceTable, StringBuilder where, List<Object> params) {
        Map<String, List<CustomMetadataFilter>> groupedFilters = criteria.getCustomMetadata().stream()
            .filter(f -> f.getField() != null && !f.getField().isEmpty() && f.getValue() != null && !f.getValue().isEmpty())
            .collect(Collectors.groupingBy(f -> {
                String col = findColumnName(sourceTable, f.getField());
                return col != null ? col : "";
            }));
            
        for (Map.Entry<String, List<CustomMetadataFilter>> entry : groupedFilters.entrySet()) {
            String colName = entry.getKey();
            if (colName.isEmpty()) continue;
            
            List<CustomMetadataFilter> filters = entry.getValue();
            where.append(" AND (");
            
            for (int i = 0; i < filters.size(); i++) {
                if (i > 0) where.append(" OR ");
                CustomMetadataFilter filter = filters.get(i);
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
            where.append(")");
        }
    }

    private String findColumnName(String table, String field) {
        String[] parts = table.split("\\.");
        String schema = parts.length > 1 ? parts[0] : "public";
        String tableName = parts.length > 1 ? parts[1] : table;
        String sql = "SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = LOWER(?)";
        List<String> cols = jdbcTemplate.queryForList(sql, String.class, schema, tableName);
        for (String c : cols) {
            if (c != null && c.matches("(?i)u[0-9a-f]+_" + field.toLowerCase())) {
                return c;
            }
        }
        return null;
    }

    private String validateIdentifier(String identifier) {
        if (identifier != null && !identifier.matches("^[a-zA-Z0-9_\\-]+$")) {
            throw new IllegalArgumentException("Invalid identifier format: " + identifier);
        }
        return identifier;
    }
}
