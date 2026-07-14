package com.db_search.controller;

import com.db_search.dto.DiscoveryCriteria;
import com.db_search.service.DiscoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/discovery")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class DiscoveryController {

    @Autowired
    private DiscoveryService discoveryService;

    @PostMapping("/{endpoint}")
    public List<Map<String, Object>> executeReport(@PathVariable String endpoint, @RequestBody DiscoveryCriteria criteria) {
        log.info("Executing report for endpoint: {}, appId: {}", endpoint, criteria.getAppId());
        return discoveryService.executeReport(endpoint, criteria);
    }

    @GetMapping("/doc-classes")
    public List<String> getDocumentClasses(@RequestParam String appId) {
        log.debug("Fetching document classes for appId: {}", appId);
        return discoveryService.getDocumentClasses(appId);
    }
}
