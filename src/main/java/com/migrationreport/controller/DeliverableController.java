package com.migrationreport.controller;

import com.migrationreport.dto.DeliverableRequest;
import com.migrationreport.dto.DeliverableRowDTO;
import com.migrationreport.dto.PaginatedResponse;
import com.migrationreport.service.DeliverableService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/deliverables")

public class DeliverableController {

    private final DeliverableService deliverableService;

    public DeliverableController(DeliverableService deliverableService) {
        this.deliverableService = deliverableService;
    }

    private String buildFilterString(DeliverableRequest request) {
        StringBuilder filters = new StringBuilder();
        
        if (request.getDocumentClass() != null && !request.getDocumentClass().isEmpty()) {
            filters.append("Class: ").append(request.getDocumentClass());
        } else {
            filters.append("Class: All");
        }
        
        if (request.getStartDate() != null || request.getEndDate() != null) {
            filters.append(", Date: ").append(request.getStartDate()).append(" to ").append(request.getEndDate());
        }
        
        if (request.getMigrationStatus() != null && !request.getMigrationStatus().isEmpty()) {
            filters.append(", Status: ").append(request.getMigrationStatus());
        }
        
        return filters.toString();
    }

    @PostMapping("/migration-report")
    public ResponseEntity<PaginatedResponse<List<java.util.Map<String, Object>>>> getMigrationReport(
            @RequestBody DeliverableRequest request) {
        log.info("Generating migration report for App: '{}', {}", request.getApplicationName(), buildFilterString(request));
        long start = System.currentTimeMillis();
        PaginatedResponse<List<Map<String, Object>>> report = deliverableService.getMigrationReport(request);
        log.info("Migration report generated in {}ms. Records: {}", System.currentTimeMillis() - start, report.getTotalRecords());
        return ResponseEntity.ok(report);
    }
}
