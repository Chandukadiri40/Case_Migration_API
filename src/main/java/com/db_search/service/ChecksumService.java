package com.db_search.service;

import com.db_search.dto.ChecksumRecordDTO;
import com.db_search.dto.ChecksumReportRequest;
import com.db_search.dto.ChecksumReportResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChecksumService {

    private final EntityManager entityManager;
    private final String checksumTable;
    private final String stagingTable;

    public ChecksumService(
            EntityManager entityManager,
            @Value("${checksum.table}") String checksumTable,
            @Value("${search.tables.staging}") String stagingTable) {
        this.entityManager = entityManager;
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

        if (request.getFromDate() != null && !request.getFromDate().trim().isEmpty()) {
            sql.append(" AND s.MIGRATED_DATE >= :fromDate");
        }

        if (request.getToDate() != null && !request.getToDate().trim().isEmpty()) {
            sql.append(" AND s.MIGRATED_DATE <= :toDate");
        }

        Query query = entityManager.createNativeQuery(sql.toString());

        if (request.getFromDate() != null && !request.getFromDate().trim().isEmpty()) {
            query.setParameter("fromDate", request.getFromDate().trim());
        }
        if (request.getToDate() != null && !request.getToDate().trim().isEmpty()) {
            query.setParameter("toDate", request.getToDate().trim());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        
        List<ChecksumRecordDTO> records = new ArrayList<>();
        for (Object[] row : results) {
            ChecksumRecordDTO dto = new ChecksumRecordDTO();
            dto.setDocumentId(row[0] != null ? row[0].toString() : null);
            dto.setChecksumBefore(row[1] != null ? row[1].toString() : null);
            dto.setChecksumAfter(row[2] != null ? row[2].toString() : null);
            dto.setFileName(row[3] != null ? row[3].toString() : null);
            dto.setChecksumStatus(row[4] != null ? row[4].toString() : null);
            dto.setMigrationStatus(row[5] != null ? row[5].toString() : null);
            dto.setDocumentTitle(row[6] != null ? row[6].toString() : null);
            dto.setDocumentClass(row[7] != null ? row[7].toString() : null);
            dto.setMigratedDate(row[8] != null ? row[8].toString() : null);
            records.add(dto);
        }

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
