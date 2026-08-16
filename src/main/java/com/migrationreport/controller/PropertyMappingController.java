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

    @GetMapping("/document-classes")
    public ResponseEntity<List<String>> getDocumentClasses(@RequestParam(required = false, defaultValue = "source") String type) {
        return ResponseEntity.ok(mappingService.getDocumentClasses(type));
    }

    @GetMapping("/class-properties")
    public ResponseEntity<List<java.util.Map<String, Object>>> getClassProperties(@RequestParam String docClass, @RequestParam(required = false, defaultValue = "source") String type) {
        return ResponseEntity.ok(mappingService.getClassProperties(docClass, type));
    }

    @PostMapping
    public ResponseEntity<PropertyMappingTemplate> saveTemplate(@RequestBody PropertyMappingTemplate template) {
        if (template.getApplicationId() == null || template.getApplicationId().isEmpty()) {
            template.setApplicationId("doctaba");
        }
        mappingService.saveTemplate(template);
        return ResponseEntity.ok(template);
    }
    
    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String templateId) {
        mappingService.deleteTemplate(templateId);
        return ResponseEntity.ok().build();
    }
}
