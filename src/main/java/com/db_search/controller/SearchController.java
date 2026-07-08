package com.db_search.controller;

import com.db_search.dto.QuerySearchRequest;
import com.db_search.dto.SearchRequest;
import com.db_search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
@CrossOrigin(origins = "*") // Allow requests from any origin (e.g. your UI frontend)
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
        List<Map<String, Object>> results = searchService.searchByCustomQuery(request.getQuery());
        return ResponseEntity.ok(results);
    }

    /**
     * Performs a unified search using custom/system filters, status, date range, and table selection.
     */
    @PostMapping
    public ResponseEntity<List<Map<String, Object>>> search(@RequestBody SearchRequest request) {
        List<Map<String, Object>> results = searchService.search(request);
        return ResponseEntity.ok(results);
    }

    /**
     * Executes a raw SQL SELECT query from the Query Executor panel.
     */
    @PostMapping("/execute-query")
    public ResponseEntity<List<Map<String, Object>>> executeQuery(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        List<Map<String, Object>> results = searchService.executeQuery(sql);
        return ResponseEntity.ok(results);
    }
}

