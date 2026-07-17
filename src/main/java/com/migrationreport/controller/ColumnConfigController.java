package com.migrationreport.controller;

import com.migrationreport.dto.MetadataFieldDTO;
import com.migrationreport.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Exposes column and table configurations for the frontend dashboard.
 */
@Slf4j
@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "*")
public class ColumnConfigController {

    private final SearchService searchService;

    public ColumnConfigController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Returns the selectable tables configuration for the UI dropdown.
     */
    @GetMapping("/tables")
    public ResponseEntity<List<Map<String, String>>> getTablesConfig() {
        log.info("Starting method: getTablesConfig");
        ResponseEntity<List<Map<String, String>>> result = ResponseEntity.ok(List.of(
                Map.of("key", "source", "label", "Source Table"),
                Map.of("key", "staging", "label", "Staging Table"),
                Map.of("key", "target", "label", "Target Table")
        ));
        log.info("Ending method: getTablesConfig");
        return result;
    }

    /**
     * Returns the standard system columns that are identical for all tables.
     */
    @GetMapping("/system-columns")
    public ResponseEntity<List<String>> getSystemColumns() {
        log.info("Starting method: getSystemColumns");
        ResponseEntity<List<String>> result = ResponseEntity.ok(List.of("doc-id", "created-date", "content-size", "mime-type"));
        log.info("Ending method: getSystemColumns");
        return result;
    }

    /**
     * Returns configured custom columns for the UI.
     * Use query parameter ?table=source (or staging, target) to fetch for specific tables.
     */
    @GetMapping("/custom-columns")
    public ResponseEntity<List<String>> getCustomColumns(@RequestParam(value = "table", defaultValue = "staging") String table) {
        log.info("Starting method: getCustomColumns with arguments: table={}", table);
        ResponseEntity<List<String>> result = ResponseEntity.ok()
            .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            .body(searchService.getCustomColumnsForTable(table));
        log.info("Ending method: getCustomColumns");
        return result;
    }

    /**
     * Returns all dynamic metadata fields (display name mapping) loaded from database or static fallback.
     */
    @GetMapping("/available-fields")
    public ResponseEntity<List<MetadataFieldDTO>> getAvailableFields() {
        log.info("Starting method: getAvailableFields");
        ResponseEntity<List<MetadataFieldDTO>> result = ResponseEntity.ok()
            .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            .body(searchService.getAvailableFields());
        log.info("Ending method: getAvailableFields");
        return result;
    }

    /**
     * Returns the configured reconciliation system properties and custom metadata columns.
     */
    @GetMapping("/reconciliation-properties")
    public ResponseEntity<Map<String, List<String>>> getReconciliationProperties() {
        log.info("Starting method: getReconciliationProperties");
        ResponseEntity<Map<String, List<String>>> result = ResponseEntity.ok(Map.of(
            "systemProperties", searchService.getReconciliationSystemProperties(),
            "customMetadata", searchService.getReconciliationCustomMetadata()
        ));
        log.info("Ending method: getReconciliationProperties");
        return result;
    }
}


