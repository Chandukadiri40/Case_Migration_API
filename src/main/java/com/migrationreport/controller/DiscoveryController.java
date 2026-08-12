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

public class DiscoveryController {

    @Autowired
    private DiscoveryService discoveryService;

    private String getReportName(String endpoint) {
        switch (endpoint) {
            case "doc-count": return "Document Count (Category: Volume)";
            case "doc-year-wise": return "Year-Wise Trend (Category: Volume)";
            case "doc-year-month": return "Year-Month Trend (Category: Volume)";
            case "doc-mime": return "MIME Type Distribution (Category: Format)";
            case "size-total": return "Total Size (Category: Size)";
            case "size-bucket": return "Size Buckets (Category: Size)";
            case "no-content": return "No Content Elements (Category: Size)";
            case "annotation-total": return "Total Annotations (Category: Components)";
            case "annotation-mime": return "Annotations by MIME (Category: Components)";
            case "version-summary": return "Version Summary (Category: Versions)";
            case "version-distribution": return "Version Distribution (Category: Versions)";
            case "property-defs": return "Property Definitions (Category: Properties)";
            case "element-total": return "Total Content Elements (Category: Elements)";
            case "element-class": return "Elements by Class (Category: Elements)";
            case "element-properties": return "Element Properties (Category: Elements)";
            case "custom-object-trend": return "Custom Object Trend (Category: Components)";
            case "retrieval-hex-blob": return "Retrieval Names Hex/Blob (Category: Hex/Blob)";
            case "component-hex-blob": return "Component Types Hex/Blob (Category: Hex/Blob)";
            case "content-hex-blob": return "Content Info Hex/Blob (Category: Hex/Blob)";
            default: return endpoint + " (Category: Unknown)";
        }
    }

    private String buildFilterString(DiscoveryCriteria criteria) {
        StringBuilder filters = new StringBuilder();
        List<String> docClasses = criteria.getDocumentClasses();
        
        // If empty (meaning 'All'), dynamically fetch the names to log them
        if (docClasses == null || docClasses.isEmpty()) {
            try {
                docClasses = discoveryService.getDocumentClasses(criteria.getAppId(), "source");
            } catch (Exception e) {
                log.warn("Could not fetch document classes for logging: {}", e.getMessage());
            }
        }
        
        filters.append("Classes: ");
        if (docClasses != null && !docClasses.isEmpty()) {
            if (docClasses.size() <= 5) {
                filters.append(docClasses);
            } else {
                List<String> sample = docClasses.subList(0, 5);
                filters.append(sample.toString().replace("]", ", ... (" + docClasses.size() + " total)]"));
            }
        } else {
            filters.append("[All]");
        }
        
        if (criteria.getMimeTypes() != null && !criteria.getMimeTypes().isEmpty()) {
            filters.append(", MIMEs: ").append(criteria.getMimeTypes().size()).append(" selected");
        }
        return filters.toString();
    }

    @PostMapping("/{endpoint}")
    public ResponseEntity<List<Map<String, Object>>> generateReport(@PathVariable String endpoint, @RequestBody DiscoveryCriteria criteria) {
        String reportName = getReportName(endpoint);
        String filters = buildFilterString(criteria);
        log.info("Generating '{}' report for App '{}', {}", reportName, criteria.getAppId(), filters);
        
        List<Map<String, Object>> result = discoveryService.executeReport(endpoint, criteria);
        // Note: Execution duration and records are logged in the Service layer to avoid redundancy.
        return ResponseEntity.ok(result);
    }

    @GetMapping("/doc-classes")
    public ResponseEntity<List<String>> getDocumentClasses(@RequestParam String appId, @RequestParam(required = false, defaultValue = "source") String type) {
        log.info("Retrieving document classes for App '{}'.", appId);
        List<String> classes = discoveryService.getDocumentClasses(appId, type);
        log.info("Retrieved {} document classes: {}", classes.size(), classes);
        return ResponseEntity.ok(classes);
    }

    @GetMapping("/class-properties")
    public ResponseEntity<List<Map<String, Object>>> getClassProperties(@RequestParam String appId, @RequestParam String docClass) {
        log.info("Retrieving class properties for class '{}'.", docClass);
        List<Map<String, Object>> properties = discoveryService.getClassProperties(appId, docClass);
        return ResponseEntity.ok(properties);
    }
}
