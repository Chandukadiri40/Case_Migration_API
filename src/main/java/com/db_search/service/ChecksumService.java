package com.db_search.service;

import com.db_search.dto.ChecksumReportRequest;
import com.db_search.dto.ChecksumReportResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChecksumService {

    private final JdbcTemplate jdbcTemplate;
    private final String checksumTable;
    private final String dateColumn;

    public ChecksumService(
            JdbcTemplate jdbcTemplate,
            @Value("${checksum.table}") String checksumTable,
            @Value("${search.date-column:CREATE_DATE}") String dateColumn) {
        this.jdbcTemplate = jdbcTemplate;
        this.checksumTable = checksumTable;
        this.dateColumn = dateColumn.toLowerCase();
    }

    public ChecksumReportResponse getReport(ChecksumReportRequest request) {
        if (request.getAppId() == null || request.getAppId().trim().isEmpty()) {
            throw new IllegalArgumentException("Application (Object Store) is required.");
        }

        String targetStagingTable = request.getAppId().trim() + ".docversion_staging";

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
           .append("c.documentid, ")
           .append("c.checksumbefore, ")
           .append("c.checksumafter, ")
           .append("c.filename, ")
           .append("c.checksum_status, ")
           .append("s.* ")
           .append("FROM ").append(request.getAppId().trim()).append(".").append(checksumTable).append(" c ")
           .append("INNER JOIN ").append(targetStagingTable).append(" s ")
           .append("ON c.documentid = s.object_id ")
           .append("WHERE 1=1");

        List<Object> params = new ArrayList<>();

        sql.append(" AND LOWER(s.migration_status) IN ('success', 'migrated')");

        if (request.getDocumentClass() != null && !request.getDocumentClass().trim().isEmpty() && !request.getDocumentClass().equalsIgnoreCase("All")) {
            sql.append(" AND s.object_class_id IN (SELECT object_id FROM ").append(request.getAppId().trim()).append(".classdef WHERE LOWER(symbolic_name) = LOWER(?))");
            params.add(request.getDocumentClass().trim());
        }

        if (request.getFromDate() != null && !request.getFromDate().trim().isEmpty()) {
            sql.append(" AND CAST(s." + dateColumn + " AS timestamp) >= CAST(? AS timestamp)");
            params.add(request.getFromDate().trim());
        }

        if (request.getToDate() != null && !request.getToDate().trim().isEmpty()) {
            sql.append(" AND CAST(s." + dateColumn + " AS timestamp) <= CAST(? AS timestamp)");
            params.add(request.getToDate().trim());
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        List<Map<String, Object>> records = new ArrayList<>();
        long total = rows.size();
        long completed = 0;
        long pending = 0;
        long migratedInStaging = 0;

        Map<String, String> classMap = getClassIdToSymbolicNameMap(request.getAppId());
        for (Map<String, Object> row : rows) {
            Map<String, Object> record = new HashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (key.equalsIgnoreCase("object_class_id") && val != null) {
                    val = classMap.getOrDefault(val.toString().toUpperCase(), val.toString());
                }
                record.put(key.toLowerCase(), val);
                record.put(key, val);
            }
            records.add(record);

            String chkStatus = (String) record.get("checksum_status");
            if ("Completed".equalsIgnoreCase(chkStatus)) {
                completed++;
            } else {
                pending++;
            }

            String migStatus = (String) record.get("migration_status");
            if ("Migrated".equalsIgnoreCase(migStatus) || "Success".equalsIgnoreCase(migStatus)) {
                migratedInStaging++;
            }
        }

        Map<String, Long> summary = new HashMap<>();
        summary.put("total", total);
        summary.put("completed", completed);
        summary.put("pending", pending);
        summary.put("migratedInStaging", migratedInStaging);

        ChecksumReportResponse response = new ChecksumReportResponse();
        response.setRecords(records);
        response.setSummary(summary);

        return response;
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
