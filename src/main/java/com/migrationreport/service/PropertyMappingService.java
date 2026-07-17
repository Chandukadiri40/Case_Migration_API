package com.migrationreport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migrationreport.dto.mapping.PropertyMappingTemplate;
import com.migrationreport.exception.ConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class PropertyMappingService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${mapping.config.file.path:config/property-mapping-templates.json}")
    private String mappingConfigFilePath;

    private List<PropertyMappingTemplate> cachedTemplates = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadTemplates();
    }

    public synchronized List<PropertyMappingTemplate> loadTemplates() {
        File file = new File(mappingConfigFilePath);
        if (!file.exists()) {
            cachedTemplates = new ArrayList<>();
            return cachedTemplates;
        }

        try {
            log.info("[MAPPING] Loading property mapping templates from {}", file.getAbsolutePath());
            cachedTemplates = objectMapper.readValue(file, new TypeReference<List<PropertyMappingTemplate>>() {});
        } catch (IOException e) {
            log.error("[MAPPING] Failed to load property mapping templates", e);
            throw new ConfigurationException("Failed to load property mapping templates: " + e.getMessage());
        }
        return cachedTemplates;
    }

    public synchronized void saveTemplate(PropertyMappingTemplate template) {
        if (template.getTemplateId() == null || template.getTemplateId().isEmpty()) {
            template.setTemplateId(UUID.randomUUID().toString());
        }

        // Replace existing template for same App & Source Class, or add new
        cachedTemplates.removeIf(t -> 
            t.getApplicationId().equals(template.getApplicationId()) && 
            t.getSourceDocumentClass().equals(template.getSourceDocumentClass())
        );

        cachedTemplates.add(template);
        saveToFile();
    }
    
    public synchronized void deleteTemplate(String templateId) {
        cachedTemplates.removeIf(t -> t.getTemplateId().equals(templateId));
        saveToFile();
    }

    private void saveToFile() {
        File file = new File(mappingConfigFilePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, cachedTemplates);
            log.info("[MAPPING] Saved property mapping templates to {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.error("[MAPPING] Failed to save property mapping templates", e);
            throw new ConfigurationException("Failed to save property mapping templates: " + e.getMessage());
        }
    }

    public List<PropertyMappingTemplate> getAllTemplates() {
        return cachedTemplates;
    }

    public List<PropertyMappingTemplate> getTemplatesByAppId(String appId) {
        return cachedTemplates.stream()
                .filter(t -> t.getApplicationId().equals(appId))
                .toList();
    }

    public Optional<PropertyMappingTemplate> getTemplateByAppAndClass(String appId, String sourceDocumentClass) {
        return cachedTemplates.stream()
                .filter(t -> t.getApplicationId().equals(appId) && t.getSourceDocumentClass().equals(sourceDocumentClass))
                .findFirst();
    }
}
