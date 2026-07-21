package com.migrationreport.controller;

import com.migrationreport.dto.QuerySearchRequest;
import com.migrationreport.dto.SearchRequest;
import com.migrationreport.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/search")
@CrossOrigin(origins = "*")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }
    /**
     * Performs a query-based search (looks for matches using custom SQL condition fragments).
     */
    @PostMapping("/query")
    public ResponseEntity<List<Map<String, Object>>> searchByQuery(@RequestBody QuerySearchRequest request) {
        log.info("Starting method: searchByQuery with arguments: {}", request);
        log.info("[SEARCH] Incoming custom query request");
        long start = System.currentTimeMillis();
        List<Map<String, Object>> results = searchService.searchByCustomQuery(request.getQuery());
        log.info("Custom query completed in {}ms. Found {} records.", System.currentTimeMillis() - start, results.size());
        log.info("Ending method: searchByQuery");
        return ResponseEntity.ok(results);
    }

    /**
     * Performs a unified search using custom/system filters, status, date range, and table selection.
     */
    @PostMapping
    public ResponseEntity<List<Map<String, Object>>> search(@RequestBody SearchRequest request) {
        log.info("Starting method: search with arguments: {}", request);
        log.info("[SEARCH] Incoming request for App ID: '{}', Target Table: '{}'", request.getAppId(), request.getTable());
        long start = System.currentTimeMillis();
        List<Map<String, Object>> results = searchService.search(request);
        log.info("Search completed in {}ms. Found {} records.", System.currentTimeMillis() - start, results.size());
        log.info("Ending method: search");
        return ResponseEntity.ok(results);
    }

    /**
     * Executes a raw SQL SELECT query from the Query Executor panel.
     */
    @PostMapping("/execute-query")
    public ResponseEntity<List<Map<String, Object>>> executeQuery(@RequestBody Map<String, String> request) {
        log.info("Starting method: executeQuery with arguments: {}", request);
        String sql = request.get("sql");
        log.info("[SEARCH] Executing raw SQL query: {}", sql.toLowerCase());
        long start = System.currentTimeMillis();
        List<Map<String, Object>> results = searchService.executeQuery(sql);
        log.info("Raw query executed in {}ms. Found {} records.", System.currentTimeMillis() - start, results.size());
        log.info("Ending method: executeQuery");
        return ResponseEntity.ok(results);
    }
}

