package com.db_search.service;

import com.db_search.dto.ExceptionCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExceptionService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, List<Map<String, Object>>> checkExceptions(ExceptionCriteria criteria) {
        String schema = criteria.getAppId() != null && !criteria.getAppId().isEmpty() ? criteria.getAppId() + "." : "";
        String sourceTable = schema + "docversion_source";
        String stagingTable = schema + "docversion_staging";
        String targetTable = schema + "docversion_target";
        
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
            where.append(" AND CAST(dv.create_date AS DATE) >= ?");
            params.add(java.sql.Date.valueOf(criteria.getCreatedFrom()));
        }
        
        if (criteria.getCreatedTo() != null) {
            where.append(" AND CAST(dv.create_date AS DATE) <= ?");
            params.add(java.sql.Date.valueOf(criteria.getCreatedTo()));
        }

        if (criteria.getCustomMetadata() != null && !criteria.getCustomMetadata().isEmpty()) {
            java.util.Map<String, List<com.db_search.dto.CustomMetadataFilter>> groupedFilters = criteria.getCustomMetadata().stream()
                .filter(f -> f.getField() != null && !f.getField().isEmpty() && f.getValue() != null && !f.getValue().isEmpty())
                .collect(Collectors.groupingBy(f -> {
                    String col = findColumnName(schema + "docversion_source", f.getField());
                    return col != null ? col : "";
                }));
                
            for (Map.Entry<String, List<com.db_search.dto.CustomMetadataFilter>> entry : groupedFilters.entrySet()) {
                String colName = entry.getKey();
                if (colName.isEmpty()) continue;
                
                List<com.db_search.dto.CustomMetadataFilter> filters = entry.getValue();
                where.append(" AND (");
                
                for (int i = 0; i < filters.size(); i++) {
                    if (i > 0) where.append(" OR ");
                    com.db_search.dto.CustomMetadataFilter filter = filters.get(i);
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
        String idSql = "SELECT dv.object_id FROM " + fromClause + where.toString() + " LIMIT 100";
        List<String> objectIds = jdbcTemplate.queryForList(idSql, params.toArray(), String.class);
        
        Map<String, List<Map<String, Object>>> result = new java.util.HashMap<>();
        if (objectIds.isEmpty()) {
            result.put("source", new ArrayList<>());
            result.put("staging", new ArrayList<>());
            result.put("target", new ArrayList<>());
            return result;
        }
        
        String inClause = objectIds.stream().map(id -> "?").collect(Collectors.joining(","));
        Object[] inParams = objectIds.toArray();
        
        List<Map<String, Object>> sourceData = jdbcTemplate.queryForList("SELECT * FROM " + sourceTable + " WHERE object_id IN (" + inClause + ")", inParams);
        List<Map<String, Object>> stagingData = jdbcTemplate.queryForList("SELECT * FROM " + stagingTable + " WHERE object_id IN (" + inClause + ")", inParams);
        List<Map<String, Object>> targetData = jdbcTemplate.queryForList("SELECT * FROM " + targetTable + " WHERE object_id IN (" + inClause + ")", inParams);
        
        result.put("source", sourceData);
        result.put("staging", stagingData);
        result.put("target", targetData);
        return result;
    }

    public List<String> getMetadataFields(String appId) {
        String schema = appId != null && !appId.isEmpty() ? appId : "public";
        String sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = 'docversion_source'";
        List<String> cols = jdbcTemplate.queryForList(sql, new Object[]{schema}, String.class);
        return cols.stream()
                   .filter(c -> c.matches("u[0-9a-fA-F]+_.*"))
                   .map(c -> c.substring(c.indexOf('_') + 1))
                   .sorted()
                   .collect(Collectors.toList());
    }

    private String findColumnName(String table, String field) {
        String[] parts = table.split("\\.");
        String schema = parts.length > 1 ? parts[0] : "public";
        String tableName = parts.length > 1 ? parts[1] : table;
        String sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?";
        List<String> cols = jdbcTemplate.queryForList(sql, new Object[]{schema, tableName}, String.class);
        for (String c : cols) {
            if (c.matches("u[0-9a-fA-F]+_" + field)) {
                return c;
            }
        }
        return null;
    }
}
