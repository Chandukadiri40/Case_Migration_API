package com.migrationreport.controller;

import com.migrationreport.dto.DeliverableRequest;
import com.migrationreport.dto.DeliverableRowDTO;
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

    @PostMapping("/migration-report")
    public ResponseEntity<List<java.util.Map<String, Object>>> getMigrationReport(
            @RequestBody DeliverableRequest request) {
        log.info("Starting method: getMigrationReport with arguments: {}", request);
        log.info("[DELIVERABLES] Generating migration report for application: {}", request.getApplicationName());
        long start = System.currentTimeMillis();
        List<Map<String, Object>> report = deliverableService.getMigrationReport(request);
        log.info("[DELIVERABLES] Migration report generated in {}ms. Rows: {}", System.currentTimeMillis() - start, report.size());
        log.info("Ending method: getMigrationReport");
        return ResponseEntity.ok(report);
    }
}
