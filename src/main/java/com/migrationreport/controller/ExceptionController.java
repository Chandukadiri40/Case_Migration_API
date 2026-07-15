package com.migrationreport.controller;

import com.migrationreport.dto.ExceptionCriteria;
import com.migrationreport.service.ExceptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/exceptions")
@CrossOrigin(origins = "*")
public class ExceptionController {

    @Autowired
    private ExceptionService exceptionService;

    @PostMapping("/check")
    public Map<String, List<Map<String, Object>>> checkExceptions(@RequestBody ExceptionCriteria criteria) {
        log.info("[EXCEPTIONS] Checking exceptions for Application: '{}'", criteria.getAppId());
        long start = System.currentTimeMillis();
        Map<String, List<Map<String, Object>>> result = exceptionService.checkExceptions(criteria);
        log.info("[EXCEPTIONS] Exception check completed in {}ms.", System.currentTimeMillis() - start);
        return result;
    }

    @GetMapping("/test-logs")
    public ResponseEntity<String> testLogging() {
        log.info("[TEST] This is an INFO level log.");
        log.debug("[TEST] This is a DEBUG level log. (You will only see this because com.migrationreport is set to DEBUG)");
        log.warn("[TEST] This is a WARN level log.");
        log.error("[TEST] This is an ERROR level log! Simulating a failure.", new RuntimeException("Simulated exception"));
        return ResponseEntity.ok("Check the console and migration-report.log for INFO, DEBUG, WARN, and ERROR logs!");
    }

    @GetMapping("/metadata-fields")
    public List<String> getMetadataFields(@RequestParam String appId) {
        log.debug("[EXCEPTIONS] Fetching metadata fields for Application: '{}'", appId);
        return exceptionService.getMetadataFields(appId);
    }
}
