package com.db_search.controller;

import com.db_search.dto.ChecksumReportRequest;
import com.db_search.dto.ChecksumReportResponse;
import com.db_search.service.ChecksumService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        ChecksumReportResponse response = checksumService.getReport(request);
        return ResponseEntity.ok(response);
    }
}
