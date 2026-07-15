package com.migrationreport.controller;

import com.migrationreport.dto.ChecksumReportRequest;
import com.migrationreport.dto.ChecksumReportResponse;
import com.migrationreport.service.ChecksumService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/checksum")
@CrossOrigin(origins = "*")
public class ChecksumController {

    private final ChecksumService checksumService;

    public ChecksumController(ChecksumService checksumService) {
        this.checksumService = checksumService;
    }

    @PostMapping("/report")
    public ResponseEntity<ChecksumReportResponse> getReport(@RequestBody ChecksumReportRequest request) {
        log.info("Starting method: getReport with arguments: {}", request);
        log.info("[CHECKSUM] Generating checksum report for Application: '{}'", request.getAppId());
        long start = System.currentTimeMillis();
        ChecksumReportResponse response = checksumService.getReport(request);
        log.info("[CHECKSUM] Checksum report generated in {}ms. Total Records Analyzed: {}", 
                 System.currentTimeMillis() - start, 
                 response.getRecords() != null ? response.getRecords().size() : 0);
        log.info("Ending method: getReport");
        return ResponseEntity.ok(response);
    }
}
