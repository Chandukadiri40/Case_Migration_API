package com.db_search.service;

import com.db_search.dto.ChecksumRecordDTO;
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
    private final String stagingTable;

    public ChecksumService(
            JdbcTemplate jdbcTemplate,
            @Value("${checksum.table}") String checksumTable,
            @Value("${search.tables.staging}") String stagingTable) {
        this.jdbcTemplate = jdbcTemplate;
        this.checksumTable = checksumTable;
        this.stagingTable = stagingTable;
    }

    public ChecksumReportResponse getReport(ChecksumReportRequest request) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
           .append("c.DOCUMENTID, ")
           .append("c.CHECKSUMBEFORE, ")
           .append("c.CHECKSUMAFTER, ")
           .append("c.FILENAME, ")
           .append("c.CHECKSUM_STATUS, ")
           .append("s.MIGRATION_STATUS, ")
           .append("s.U1708_DOCUMENTTITLE, ")
           .append("s.OBJECT_CLASS_ID, ")
           .append("CONVERT(VARCHAR(30), s.MIGRATED_DATE, 126) AS MIGRATED_DATE ")
           .append("FROM ").append(checksumTable).append(" c ")
           .append("LEFT JOIN ").append(stagingTable).append(" s ")
           .append("ON c.DOCUMENTID = s.OBJECT_ID ")
           .append("WHERE 1=1");

        List<Object> params = new ArrayList<>();

        if (request.getFromDate() != null && !request.getFromDate().trim().isEmpty()) {
            sql.append(" AND s.MIGRATED_DATE >= ?");
            params.add(request.getFromDate().trim());
        }

        if (request.getToDate() != null && !request.getToDate().trim().isEmpty()) {
            sql.append(" AND s.MIGRATED_DATE <= ?");
            params.add(request.getToDate().trim());
        }

        List<ChecksumRecordDTO> records = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            ChecksumRecordDTO dto = new ChecksumRecordDTO();
            dto.setDocumentId(rs.getString("DOCUMENTID"));
            dto.setChecksumBefore(rs.getString("CHECKSUMBEFORE"));
            dto.setChecksumAfter(rs.getString("CHECKSUMAFTER"));
            dto.setFileName(rs.getString("FILENAME"));
            dto.setChecksumStatus(rs.getString("CHECKSUM_STATUS"));
            dto.setMigrationStatus(rs.getString("MIGRATION_STATUS"));
            dto.setMigratedDate(rs.getString("MIGRATED_DATE"));
            dto.setDocumentTitle(rs.getString("U1708_DOCUMENTTITLE"));
            dto.setDocumentClass(rs.getString("OBJECT_CLASS_ID"));
            return dto;
        }, params.toArray());

        long total = records.size();
        long completed = records.stream()
                .filter(r -> "Completed".equalsIgnoreCase(r.getChecksumStatus()))
                .count();
        long pending = records.stream()
                .filter(r -> !"Completed".equalsIgnoreCase(r.getChecksumStatus()))
                .count();
        long migratedInStaging = records.stream()
                .filter(r -> "Migrated".equalsIgnoreCase(r.getMigrationStatus()))
                .count();

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
}
