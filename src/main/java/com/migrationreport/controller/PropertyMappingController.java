package com.migrationreport.controller;

import com.migrationreport.dto.mapping.PropertyMappingTemplate;
import com.migrationreport.service.PropertyMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/property-mappings")
@RequiredArgsConstructor

public class PropertyMappingController {

    private final PropertyMappingService mappingService;

    @GetMapping
    public ResponseEntity<List<PropertyMappingTemplate>> getAllTemplates() {
        return ResponseEntity.ok(mappingService.getAllTemplates());
    }

    @GetMapping("/app/{appId}")
    public ResponseEntity<List<PropertyMappingTemplate>> getTemplatesByApp(@PathVariable String appId) {
        return ResponseEntity.ok(mappingService.getTemplatesByAppId(appId));
    }

    @PostMapping
    public ResponseEntity<PropertyMappingTemplate> saveTemplate(@RequestBody PropertyMappingTemplate template) {
        mappingService.saveTemplate(template);
        return ResponseEntity.ok(template);
    }
    
    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String templateId) {
        mappingService.deleteTemplate(templateId);
        return ResponseEntity.ok().build();
    }
}
