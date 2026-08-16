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
        } else {
            // Remove existing template if updating
            cachedTemplates.removeIf(t -> t.getTemplateId().equals(template.getTemplateId()));
        }

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

    @Value("${document.classes.definition.path:config/document-classes-definition.json}")
    private String documentClassesDefinitionPath;

    public List<PropertyMappingTemplate> getAllTemplates() {
        return cachedTemplates;
    }

    public List<PropertyMappingTemplate> getTemplatesByAppId(String appId) {
        return cachedTemplates.stream()
                .filter(t -> t.getApplicationId() != null && t.getApplicationId().equalsIgnoreCase(appId))
                .toList();
    }

    public Optional<PropertyMappingTemplate> getTemplateByAppAndClass(String appId, String sourceDocumentClass) {
        return cachedTemplates.stream()
                .filter(t -> (appId == null || t.getApplicationId() == null || t.getApplicationId().equalsIgnoreCase(appId)) 
                        && t.getSourceDocumentClass() != null && t.getSourceDocumentClass().equalsIgnoreCase(sourceDocumentClass))
                .findFirst();
    }

    public List<String> getDocumentClasses(String type) {
        File file = new File(documentClassesDefinitionPath);
        if (file.exists()) {
            try {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(file);
                com.fasterxml.jackson.databind.JsonNode docClassesNode = root.get("documentClasses");
                if (docClassesNode != null && docClassesNode.isArray()) {
                    List<String> list = new ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode node : docClassesNode) {
                        String className = "target".equalsIgnoreCase(type) 
                            ? (node.has("targetClass") ? node.get("targetClass").asText() : node.get("className").asText())
                            : node.get("className").asText();
                        if (!list.contains(className)) {
                            list.add(className);
                        }
                    }
                    return list;
                }
            } catch (Exception e) {
                log.error("[MAPPING] Error reading document classes from " + file.getAbsolutePath(), e);
            }
        }
        return "target".equalsIgnoreCase(type) 
            ? List.of("PolicyDocument", "Claim", "Complaint", "Service")
            : List.of("PolicyDoc", "Claim", "Complaint", "Service");
    }

    public List<java.util.Map<String, Object>> getClassProperties(String docClass, String type) {
        File file = new File(documentClassesDefinitionPath);
        if (file.exists()) {
            try {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(file);
                com.fasterxml.jackson.databind.JsonNode docClassesNode = root.get("documentClasses");
                if (docClassesNode != null && docClassesNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode node : docClassesNode) {
                        String className = node.has("className") ? node.get("className").asText() : "";
                        String targetClass = node.has("targetClass") ? node.get("targetClass").asText() : className;
                        String displayName = node.has("displayName") ? node.get("displayName").asText() : className;
                        
                        if (className.equalsIgnoreCase(docClass) || targetClass.equalsIgnoreCase(docClass) || displayName.equalsIgnoreCase(docClass)) {
                            com.fasterxml.jackson.databind.JsonNode propsNode = node.get("properties");
                            if (propsNode != null && propsNode.isArray()) {
                                List<java.util.Map<String, Object>> list = new ArrayList<>();
                                for (com.fasterxml.jackson.databind.JsonNode pNode : propsNode) {
                                    java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                                    if ("target".equalsIgnoreCase(type)) {
                                        String targetProp = pNode.has("targetProperty") ? pNode.get("targetProperty").asText() : pNode.get("sourceProperty").asText();
                                        String targetSym = pNode.has("targetSymbolicName") ? pNode.get("targetSymbolicName").asText() : targetProp;
                                        String targetDt = pNode.has("targetDataType") ? pNode.get("targetDataType").asText() : "STRING";
                                        map.put("propertyName", targetProp);
                                        map.put("symbolicName", targetSym);
                                        map.put("dataType", targetDt);
                                    } else {
                                        String srcProp = pNode.has("sourceProperty") ? pNode.get("sourceProperty").asText() : "";
                                        String srcSym = pNode.has("sourceSymbolicName") ? pNode.get("sourceSymbolicName").asText() : srcProp;
                                        String srcDt = pNode.has("sourceDataType") ? pNode.get("sourceDataType").asText() : "character varying";
                                        String targetProp = pNode.has("targetProperty") ? pNode.get("targetProperty").asText() : "";
                                        String targetDt = pNode.has("targetDataType") ? pNode.get("targetDataType").asText() : "STRING";
                                        map.put("propertyName", srcProp);
                                        map.put("symbolicName", srcSym);
                                        map.put("dataType", srcDt);
                                        map.put("targetProperty", targetProp);
                                        map.put("targetDataType", targetDt);
                                    }
                                    list.add(map);
                                }
                                return list;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[MAPPING] Error reading class properties from " + file.getAbsolutePath(), e);
            }
        }
        return new ArrayList<>();
    }
}
