package com.db_search.controller;

import com.db_search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Exposes column and table configurations for the frontend dashboard.
 */
@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
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
        return ResponseEntity.ok(List.of(
                Map.of("key", "source", "label", "Source Table"),
                Map.of("key", "staging", "label", "Staging Table"),
                Map.of("key", "target", "label", "Target Table")
        ));
    }

    /**
     * Returns the standard system columns that are identical for all tables.
     */
    @GetMapping("/system-columns")
    public ResponseEntity<List<String>> getSystemColumns() {
        return ResponseEntity.ok(List.of("doc-id", "created-date", "content-size", "mime-type"));
    }

    /**
     * Returns configured custom columns for the UI.
     * Use query parameter ?table=source (or staging, target) to fetch for specific tables.
     */
    @GetMapping("/custom-columns")
    public ResponseEntity<List<String>> getCustomColumns(@RequestParam(value = "table", defaultValue = "staging") String table) {
        return ResponseEntity.ok()
            .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            .body(searchService.getCustomColumnsForTable(table));
    }

    /**
     * Returns all dynamic metadata fields (display name mapping) loaded from database or static fallback.
     */
    @GetMapping("/available-fields")
    public ResponseEntity<List<com.db_search.dto.MetadataFieldDTO>> getAvailableFields() {
        return ResponseEntity.ok()
            .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            .body(searchService.getAvailableFields());
    }


}


