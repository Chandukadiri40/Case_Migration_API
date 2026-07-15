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
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ExceptionService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.springframework.beans.factory.annotation.Value("${search.system-columns.created-date:CREATE_DATE}")
    private String createdDateColumn;

    @Autowired
    private SearchService searchService;
    
    @Autowired
    private ConfigurationService configurationService;
    
    @Autowired
    private com.migrationreport.dialect.SqlDialect dialect;

    public Map<String, List<Map<String, Object>>> checkExceptions(ExceptionCriteria criteria) {
        String schema = criteria.getAppId() != null && !criteria.getAppId().isEmpty() ? criteria.getAppId() + "." : "";
        TenantConfig.ApplicationConfig appConfig = null;
        if (criteria.getAppId() != null) {
            appConfig = configurationService.getApplicationConfig(criteria.getAppId());
            if (appConfig != null && appConfig.getSchema() != null && !appConfig.getSchema().isEmpty()) {
                schema = appConfig.getSchema() + ".";
            }
        }

        if (appConfig == null || appConfig.getClassifiedTables() == null) {
            throw new com.migrationreport.exception.ResourceNotFoundException("Configuration missing for application: " + criteria.getAppId());
        }
        
        if (appConfig.getClassifiedTables().get("source") == null || appConfig.getClassifiedTables().get("source").isEmpty()) {
            throw new com.migrationreport.exception.ResourceNotFoundException("Source table not configured for application: " + criteria.getAppId());
        }
        if (appConfig.getClassifiedTables().get("staging") == null || appConfig.getClassifiedTables().get("staging").isEmpty()) {
            throw new com.migrationreport.exception.ResourceNotFoundException("Staging table not configured for application: " + criteria.getAppId());
        }
        if (appConfig.getClassifiedTables().get("target") == null || appConfig.getClassifiedTables().get("target").isEmpty()) {
            throw new com.migrationreport.exception.ResourceNotFoundException("Target table not configured for application: " + criteria.getAppId());
        }
        
        String sourceTableName = appConfig.getClassifiedTables().get("source").get(0);
        String stagingTableName = appConfig.getClassifiedTables().get("staging").get(0);
        String targetTableName = appConfig.getClassifiedTables().get("target").get(0);

        String sourceTable = schema + sourceTableName;
        String stagingTable = schema + stagingTableName;
        String targetTable = schema + targetTableName;
        
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        String fromClause = sourceTable + " dv";
        
        if (criteria.getObjectId() != null && !criteria.getObjectId().isEmpty()) {
            where.append(" AND dv.object_id = ?");
            params.add(criteria.getObjectId());
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
            java.util.Map<String, List<com.migrationreport.dto.CustomMetadataFilter>> groupedFilters = criteria.getCustomMetadata().stream()
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
                        where.append("dv.").append(colName).append(" ILIKE ?");
                        params.add("%" + filter.getValue() + "%");
                    } else if ("STARTS_WITH".equalsIgnoreCase(op)) {
                        where.append("dv.").append(colName).append(" ILIKE ?");
                        params.add(filter.getValue() + "%");
                    } else if ("ENDS_WITH".equalsIgnoreCase(op)) {
                        where.append("dv.").append(colName).append(" ILIKE ?");
                        params.add("%" + filter.getValue());
                    } else {
                        where.append("dv.").append(colName).append(" = ?");
                        params.add(filter.getValue());
                    }
                }
                where.append(")");
            }
        }

        // Get matching object_ids from source
        String idSql = "SELECT dv.object_id FROM " + fromClause + where.toString();
        log.info("[EXCEPTIONS] Executing lookup SQL: {} | Params: {}", idSql, params);
        long startId = System.currentTimeMillis();
        List<String> objectIds = jdbcTemplate.queryForList(idSql, params.toArray(), String.class);
        log.info("[EXCEPTIONS] Found {} target IDs in {}ms", objectIds.size(), System.currentTimeMillis() - startId);
        
        Map<String, List<Map<String, Object>>> result = new java.util.HashMap<>();
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
        List<Map<String, Object>> sourceData = jdbcTemplate.queryForList("SELECT " + selectColsSource + " FROM " + sourceTable + " WHERE object_id IN (" + inClause + ")", inParams);
        List<Map<String, Object>> stagingData = jdbcTemplate.queryForList("SELECT " + selectColsStaging + " FROM " + stagingTable + " WHERE object_id IN (" + inClause + ")", inParams);
        List<Map<String, Object>> targetData = jdbcTemplate.queryForList("SELECT " + selectColsTarget + " FROM " + targetTable + " WHERE object_id IN (" + inClause + ")", inParams);
        log.info("[EXCEPTIONS] Data retrieval completed in {}ms. Source: {}, Staging: {}, Target: {}", System.currentTimeMillis() - startData, sourceData.size(), stagingData.size(), targetData.size());
        
        result.put("source", sourceData);
        result.put("staging", stagingData);
        result.put("target", targetData);
        return result;
    }

    public List<String> getMetadataFields(String appId) {
        com.migrationreport.dto.config.TenantConfig.ApplicationConfig appConfig = configurationService.getApplicationConfig(appId);
        if (appConfig == null || appConfig.getClassifiedTables() == null || appConfig.getClassifiedTables().get("source") == null || appConfig.getClassifiedTables().get("source").isEmpty()) {
            throw new com.migrationreport.exception.ResourceNotFoundException("Source table configuration missing for application: " + appId);
        }
        
        String schema = appConfig.getSchema() != null && !appConfig.getSchema().isEmpty() ? appConfig.getSchema() : "public";
        String tableName = appConfig.getClassifiedTables().get("source").get(0);
        String sql = "SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = LOWER(?)";
        List<String> cols = jdbcTemplate.queryForList(sql, new Object[]{schema, tableName}, String.class);
        return cols.stream()
                   .filter(c -> c != null && c.matches("(?i)u[0-9a-f]+_.*"))
                   .map(c -> c.substring(c.indexOf('_') + 1))
                   .sorted()
                   .collect(Collectors.toList());
    }

    private String findColumnName(String table, String field) {
        String[] parts = table.split("\\.");
        String schema = parts.length > 1 ? parts[0] : "public";
        String tableName = parts.length > 1 ? parts[1] : table;
        String sql = "SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_schema) = LOWER(?) AND LOWER(table_name) = LOWER(?)";
        List<String> cols = jdbcTemplate.queryForList(sql, new Object[]{schema, tableName}, String.class);
        for (String c : cols) {
            if (c != null && c.matches("(?i)u[0-9a-f]+_" + field.toLowerCase())) {
                return c;
            }
        }
        return null;
    }
}
