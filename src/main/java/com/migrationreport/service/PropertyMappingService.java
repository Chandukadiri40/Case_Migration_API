package com.migrationreport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migrationreport.dto.mapping.PropertyMappingTemplate;
import com.migrationreport.exception.ConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

    @Value("${document.classes.definition.path:config/document-classes-definition.json}")
    private String documentClassesDefinitionPath;

    private List<PropertyMappingTemplate> cachedTemplates = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadTemplates();
    }

    public synchronized List<PropertyMappingTemplate> loadTemplates() {
        File file = new File(mappingConfigFilePath);
        if (file.exists() && file.length() > 0) {
            try {
                log.info("[MAPPING] Loading property mapping templates from file: {}", file.getAbsolutePath());
                cachedTemplates = objectMapper.readValue(file, new TypeReference<List<PropertyMappingTemplate>>() {});
                if (cachedTemplates != null && !cachedTemplates.isEmpty()) {
                    return cachedTemplates;
                }
            } catch (IOException e) {
                log.warn("[MAPPING] Failed to read external templates file {}, falling back to classpath: {}", file.getAbsolutePath(), e.getMessage());
            }
        }

        // Classpath fallback
        try {
            ClassPathResource resource = new ClassPathResource("config/property-mapping-templates.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    cachedTemplates = objectMapper.readValue(is, new TypeReference<List<PropertyMappingTemplate>>() {});
                    log.info("[MAPPING] Loaded {} templates from classpath resource config/property-mapping-templates.json", cachedTemplates.size());
                    return cachedTemplates;
                }
            }
        } catch (Exception e) {
            log.warn("[MAPPING] Could not load templates from classpath: {}", e.getMessage());
        }

        cachedTemplates = new ArrayList<>();
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

    public List<PropertyMappingTemplate> getAllTemplates() {
        if (cachedTemplates == null || cachedTemplates.isEmpty()) {
            loadTemplates();
        }
        return cachedTemplates;
    }

    public List<PropertyMappingTemplate> getTemplatesByAppId(String appId) {
        return getAllTemplates().stream()
                .filter(t -> t.getApplicationId() != null && t.getApplicationId().equalsIgnoreCase(appId))
                .toList();
    }

    public Optional<PropertyMappingTemplate> getTemplateByAppAndClass(String appId, String sourceDocumentClass) {
        return getAllTemplates().stream()
                .filter(t -> (appId == null || t.getApplicationId() == null || t.getApplicationId().equalsIgnoreCase(appId)) 
                        && t.getSourceDocumentClass() != null && t.getSourceDocumentClass().equalsIgnoreCase(sourceDocumentClass))
                .findFirst();
    }

    private JsonNode loadDocumentClassesJson() {
        File file = new File(documentClassesDefinitionPath);
        if (file.exists() && file.length() > 0) {
            try {
                return objectMapper.readTree(file);
            } catch (Exception e) {
                log.warn("[MAPPING] Could not read file {}, falling back to classpath: {}", file.getAbsolutePath(), e.getMessage());
            }
        }
        try {
            ClassPathResource resource = new ClassPathResource("config/document-classes-definition.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return objectMapper.readTree(is);
                }
            }
        } catch (Exception e) {
            log.warn("[MAPPING] Could not load document-classes-definition from classpath: {}", e.getMessage());
        }
        return null;
    }

    public List<String> getDocumentClasses(String type) {
        JsonNode root = loadDocumentClassesJson();
        if (root != null) {
            JsonNode docClassesNode = root.get("documentClasses");
            if (docClassesNode != null && docClassesNode.isArray()) {
                List<String> list = new ArrayList<>();
                for (JsonNode node : docClassesNode) {
                    String className = "target".equalsIgnoreCase(type) 
                        ? (node.has("targetClass") ? node.get("targetClass").asText() : node.get("className").asText())
                        : node.get("className").asText();
                    if (!list.contains(className)) {
                        list.add(className);
                    }
                }
                return list;
            }
        }
        return "target".equalsIgnoreCase(type) 
            ? List.of("PolicyDocument", "Claim", "Complaint", "Service")
            : List.of("PolicyDoc", "Claim", "Complaint", "Service");
    }

    public List<java.util.Map<String, Object>> getClassProperties(String docClass, String type) {
        JsonNode root = loadDocumentClassesJson();
        if (root != null) {
            JsonNode docClassesNode = root.get("documentClasses");
            if (docClassesNode != null && docClassesNode.isArray()) {
                for (JsonNode node : docClassesNode) {
                    String className = node.has("className") ? node.get("className").asText() : "";
                    String targetClass = node.has("targetClass") ? node.get("targetClass").asText() : className;
                    String displayName = node.has("displayName") ? node.get("displayName").asText() : className;
                    
                    if (className.equalsIgnoreCase(docClass) || targetClass.equalsIgnoreCase(docClass) || displayName.equalsIgnoreCase(docClass)) {
                        JsonNode propsNode = node.get("properties");
                        if (propsNode != null && propsNode.isArray()) {
                            List<java.util.Map<String, Object>> list = new ArrayList<>();
                            for (JsonNode pNode : propsNode) {
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
        }
        return new ArrayList<>();
    }
}
