package com.migrationreport.controller;

import com.migrationreport.dto.ExceptionCriteria;
import com.migrationreport.dto.PaginatedResponse;
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

public class ExceptionController {

    @Autowired
    private ExceptionService exceptionService;

    private String buildFilterString(ExceptionCriteria criteria) {
        StringBuilder filters = new StringBuilder();
        
        if (criteria.getDocumentClasses() != null && !criteria.getDocumentClasses().isEmpty()) {
            filters.append("Classes: ").append(criteria.getDocumentClasses());
        } else {
            filters.append("Classes: [All]");
        }
        
        if (criteria.getObjectId() != null && !criteria.getObjectId().trim().isEmpty()) {
            filters.append(", ObjectId: '").append(criteria.getObjectId()).append("'");
        }
        
        if (criteria.getCreatedFrom() != null || criteria.getCreatedTo() != null) {
            filters.append(", Date: ").append(criteria.getCreatedFrom()).append(" to ").append(criteria.getCreatedTo());
        }
        
        if (criteria.getCustomMetadata() != null && !criteria.getCustomMetadata().isEmpty()) {
            java.util.List<String> customStrs = criteria.getCustomMetadata().stream()
                .map(f -> f.getField() + "=" + f.getValue())
                .collect(java.util.stream.Collectors.toList());
            filters.append(", Custom Filters: ").append(customStrs);
        }
        
        return filters.toString();
    }

    @PostMapping("/check")
    public PaginatedResponse<Map<String, List<Map<String, Object>>>> checkExceptions(@RequestBody ExceptionCriteria criteria) {
        log.info("Checking exceptions for App: '{}', {}", criteria.getAppId(), buildFilterString(criteria));
        long start = System.currentTimeMillis();
        PaginatedResponse<Map<String, List<Map<String, Object>>>> result = exceptionService.checkExceptions(criteria);
        log.info("Exception check completed in {}ms.", System.currentTimeMillis() - start);
        return result;
    }

    @GetMapping("/metadata-fields")
    public List<String> getMetadataFields(@RequestParam String appId, @RequestParam(required = false) String documentClass) {
        log.info("Fetching custom metadata fields for Application: '{}', DocumentClass: '{}'", appId, documentClass);
        return exceptionService.getMetadataFields(appId, documentClass);
    }
}
