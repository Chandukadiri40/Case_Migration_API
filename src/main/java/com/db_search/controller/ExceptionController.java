package com.db_search.controller;

import com.db_search.dto.ExceptionCriteria;
import com.db_search.service.ExceptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exceptions")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class ExceptionController {

    @Autowired
    private ExceptionService exceptionService;

    @PostMapping("/check")
    public Map<String, List<Map<String, Object>>> checkExceptions(@RequestBody ExceptionCriteria criteria) {
        return exceptionService.checkExceptions(criteria);
    }

    @GetMapping("/metadata-fields")
    public List<String> getMetadataFields(@RequestParam String appId) {
        return exceptionService.getMetadataFields(appId);
    }
}
