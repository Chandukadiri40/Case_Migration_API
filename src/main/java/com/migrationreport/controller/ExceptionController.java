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
        log.info("Starting method: checkExceptions with arguments: criteria={}", criteria);
        log.info("[EXCEPTIONS] Checking exceptions for Application: '{}'", criteria.getAppId());
        long start = System.currentTimeMillis();
        Map<String, List<Map<String, Object>>> result = exceptionService.checkExceptions(criteria);
        log.info("[EXCEPTIONS] Exception check completed in {}ms.", System.currentTimeMillis() - start);
        log.info("Ending method: checkExceptions");
        return result;
    }

    @GetMapping("/metadata-fields")
    public List<String> getMetadataFields(@RequestParam String appId, @RequestParam(required = false) String documentClass) {
        log.info("Starting method: getMetadataFields with arguments: appId={}, documentClass={}", appId, documentClass);
        log.debug("[EXCEPTIONS] Fetching metadata fields for Application: '{}', DocumentClass: '{}'", appId, documentClass);
        List<String> result = exceptionService.getMetadataFields(appId, documentClass);
        log.info("Ending method: getMetadataFields");
        return result;
    }
}
