package com.migrationreport.controller;

import com.migrationreport.dto.ExceptionCriteria;
import com.migrationreport.service.ExceptionService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @PostMapping("/check")
    public Map<String, List<Map<String, Object>>> checkExceptions(@RequestBody ExceptionCriteria criteria) {
        log.info("[EXCEPTIONS] Checking exceptions for Application: '{}'", criteria.getAppId());
        long start = System.currentTimeMillis();
        Map<String, List<Map<String, Object>>> result = exceptionService.checkExceptions(criteria);
        log.info("[EXCEPTIONS] Exception check completed in {}ms.", System.currentTimeMillis() - start);
        return result;
    }

    @GetMapping("/metadata-fields")
    public List<String> getMetadataFields(@RequestParam String appId) {
        log.debug("[EXCEPTIONS] Fetching metadata fields for Application: '{}'", appId);
        return exceptionService.getMetadataFields(appId);
    }
}
