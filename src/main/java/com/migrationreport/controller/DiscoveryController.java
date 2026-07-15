package com.migrationreport.controller;

import com.migrationreport.dto.DiscoveryCriteria;
import com.migrationreport.service.DiscoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/discovery")
@CrossOrigin(origins = "*")
public class DiscoveryController {

    @Autowired
    private DiscoveryService discoveryService;

    @PostMapping("/{endpoint}")
    public ResponseEntity<List<Map<String, Object>>> generateReport(@PathVariable String endpoint, @RequestBody DiscoveryCriteria criteria) {
        log.info("Starting method: generateReport with arguments: endpoint={}, criteria={}", endpoint, criteria);
        log.info("[DISCOVERY] Generating '{}' report for App: '{}'", endpoint, criteria.getAppId());
        long start = System.currentTimeMillis();
        List<Map<String, Object>> result = discoveryService.executeReport(endpoint, criteria);
        log.info("[DISCOVERY] '{}' report generated in {}ms. Found {} records.", endpoint, System.currentTimeMillis() - start, result.size());
        log.info("Ending method: generateReport");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/doc-classes")
    public ResponseEntity<List<String>> getDocumentClasses(@RequestParam String appId) {
        log.info("Starting method: getDocumentClasses with arguments: appId={}", appId);
        List<String> classes = discoveryService.getDocumentClasses(appId);
        log.info("Ending method: getDocumentClasses");
        return ResponseEntity.ok(classes);
    }
}
