package com.migrationreport.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migrationreport.dto.config.DbConfigWrapper;
import com.migrationreport.dto.mapping.PropertyMappingTemplate;
import com.migrationreport.exception.ConfigurationException;
import com.migrationreport.util.EncryptionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

@Slf4j
@Service
public class PropertyMappingService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConfigurationService configurationService;

    @Value("${mapping.config.file.path:config/property-mapping-templates.json}")
    private String mappingConfigFilePath;

    @Value("${document.classes.definition.path:config/document-classes-definition.json}")
    private String documentClassesDefinitionPath;

    @Value("${target.document.classes.path:config/target-document-classes.json}")
    private String targetDocumentClassesPath;

    private List<PropertyMappingTemplate> cachedTemplates = new ArrayList<>();

    public PropertyMappingService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

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

    // --- Helper to execute query on FilenetDB ---
    private Connection getFilenetDbConnection() throws Exception {
        DbConfigWrapper dbConfig = configurationService.getDbConfig();
        String url = "jdbc:postgresql://192.168.1.143:5432/FilenetDB";
        String username = "postgres";
        String password = "123";
        String driver = "org.postgresql.Driver";

        if (dbConfig != null && dbConfig.getDatabases() != null && !dbConfig.getDatabases().isEmpty()) {
            Map<String, String> dbProps = dbConfig.getDatabases().get(0);
            if (dbProps.get("username") != null) username = dbProps.get("username");
            if (dbProps.get("password") != null) {
                String rawPass = dbProps.get("password");
                if (rawPass.startsWith("ENC(")) {
                    try {
                        password = EncryptionUtil.decrypt(rawPass);
                    } catch (Exception e) {
                        password = "123";
                    }
                } else if (!rawPass.equals("********")) {
                    password = rawPass;
                }
            }
            if (dbProps.get("driver") != null) driver = dbProps.get("driver");
        }

        Class.forName(driver);
        return DriverManager.getConnection(url, username, password);
    }

    // --- Datatype Code Converter ---
    public String mapColDatatypeCodeToName(Object codeObj) {
        if (codeObj == null) return "VARCHAR";
        String codeStr = codeObj.toString().trim();
        return switch (codeStr) {
            case "8" -> "VARCHAR";
            case "3" -> "DATE";
            case "2" -> "BOOLEAN";
            case "1" -> "INTEGER";
            case "4" -> "FLOAT";
            case "9" -> "GUID";
            default -> {
                String lower = codeStr.toLowerCase();
                if (lower.contains("date") || lower.contains("time")) yield "DATE";
                if (lower.contains("int") || lower.contains("long")) yield "INTEGER";
                if (lower.contains("bool")) yield "BOOLEAN";
                if (lower.contains("float") || lower.contains("double") || lower.contains("num")) yield "FLOAT";
                yield "VARCHAR";
            }
        };
    }

    // --- Dynamic Source Document Classes directly from FilenetDB ---
    public List<String> getFilenetDbSourceClasses() {
        String sql = "SELECT object_id, symbolic_name FROM public.classdefinition WHERE object_id IN (SELECT object_class_id FROM public.docversion GROUP BY object_class_id)";
        List<String> result = new ArrayList<>();
        try (Connection conn = getFilenetDbConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("symbolic_name");
                if (name != null && !name.trim().isEmpty() && !result.contains(name.trim())) {
                    result.add(name.trim());
                }
            }
            log.info("[MAPPING] Retrieved {} Source Document Classes directly from FilenetDB: {}", result.size(), result);
            if (!result.isEmpty()) return result;
        } catch (Exception e) {
            log.warn("[MAPPING] Could not query FilenetDB classdefinition directly: {}. Falling back to default list.", e.getMessage());
        }
        return List.of("Pronto_TULO", "policydocs", "doctaba_staging_table", "ClaimsMetadata");
    }

    // --- Dynamic Source Class Properties directly from FilenetDB ---
    public List<Map<String, Object>> getFilenetDbSourceClassProperties(String className) {
        String sql = "SELECT cd.COLUMN_NAME AS COLUMN_NAME, gpd.SYMBOLIC_NAME AS SYMBOLIC_NAME, pd.dbg_class_name, " +
                "pd.DBG_DISPLAY_NAME AS DISPLAY_NAME, pd.DATATYPE AS PROP_DATATYPE_CODE, " +
                "cd.COLUMN_DATATYPE AS COL_DATATYPE_CODE, cd.COLUMN_SIZE AS COL_SIZE, " +
                "pd.MAX_LENGTH AS PROP_MAX_LENGTH, pd.CARDINALITY AS CARDINALITY " +
                "FROM PROPERTYDEFINITION pd JOIN COLUMNDEFINITION cd ON pd.COLUMN_ID = cd.OBJECT_ID " +
                "JOIN GLOBALPROPERTYDEF gpd ON pd.GLOBAL_PROP_ID = gpd.OBJECT_ID " +
                "WHERE cd.DBG_TABLE_NAME = 'DocVersion' AND pd.dbg_class_name = ? " +
                "ORDER BY cd.COLUMN_NAME";
        List<Map<String, Object>> props = new ArrayList<>();
        try (Connection conn = getFilenetDbConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, className);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    String symName = rs.getString("SYMBOLIC_NAME");
                    String dispName = rs.getString("DISPLAY_NAME");
                    Object colTypeObj = rs.getObject("COL_DATATYPE_CODE");
                    if (colTypeObj == null) colTypeObj = rs.getObject("PROP_DATATYPE_CODE");
                    String dataType = mapColDatatypeCodeToName(colTypeObj);
                    String colSize = rs.getString("COL_SIZE");

                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("propertyName", colName != null ? colName : symName);
                    map.put("symbolicName", symName != null ? symName : colName);
                    map.put("displayName", dispName != null ? dispName : (symName != null ? symName : colName));
                    map.put("dataType", dataType);
                    map.put("columnSize", colSize);
                    props.add(map);
                }
            }
            log.info("[MAPPING] Retrieved {} source properties from FilenetDB for class: {}", props.size(), className);
            if (!props.isEmpty()) return props;
        } catch (Exception e) {
            log.warn("[MAPPING] Could not query FilenetDB properties for class {}: {}", className, e.getMessage());
        }
        return new ArrayList<>();
    }

    // --- Target Document Classes from FilenetDB with JSON Fallback ---
    public List<String> getTargetDocumentClasses() {
        String sql = "SELECT DISTINCT symbolic_name FROM public.classdefinition WHERE symbolic_name IS NOT NULL AND symbolic_name != '' AND (is_hidden = false OR is_hidden IS NULL) ORDER BY symbolic_name";
        List<String> result = new ArrayList<>();
        try (Connection conn = getFilenetDbConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("symbolic_name");
                if (name != null && !name.trim().isEmpty() && !result.contains(name.trim())) {
                    result.add(name.trim());
                }
            }
            log.info("[MAPPING] Retrieved {} Target Document Classes directly from FilenetDB: {}", result.size(), result);
            if (!result.isEmpty()) return result;
        } catch (Exception e) {
            log.warn("[MAPPING] Could not query FilenetDB classdefinition for target classes: {}. Falling back to target-document-classes.json.", e.getMessage());
        }

        JsonNode root = loadTargetDocumentClassesJson();
        if (root != null) {
            JsonNode docClassesNode = root.get("documentClasses");
            if (docClassesNode != null && docClassesNode.isArray()) {
                List<String> list = new ArrayList<>();
                for (JsonNode node : docClassesNode) {
                    String className = node.has("className") ? node.get("className").asText() : "";
                    if (!className.isEmpty() && !list.contains(className)) {
                        list.add(className);
                    }
                }
                if (!list.isEmpty()) return list;
            }
        }
        return List.of("PolicyDocument", "ClaimDocument", "InvoiceDocument", "BulkImporting", "BankAccount");
    }

    public List<Map<String, Object>> getTargetClassProperties(String targetClass) {
        String sql = "SELECT cd.COLUMN_NAME AS COLUMN_NAME, gpd.SYMBOLIC_NAME AS SYMBOLIC_NAME, pd.dbg_class_name, " +
                "pd.DBG_DISPLAY_NAME AS DISPLAY_NAME, pd.DATATYPE AS PROP_DATATYPE_CODE, " +
                "cd.COLUMN_DATATYPE AS COL_DATATYPE_CODE, cd.COLUMN_SIZE AS COL_SIZE, " +
                "pd.MAX_LENGTH AS PROP_MAX_LENGTH, pd.CARDINALITY AS CARDINALITY " +
                "FROM PROPERTYDEFINITION pd JOIN COLUMNDEFINITION cd ON pd.COLUMN_ID = cd.OBJECT_ID " +
                "JOIN GLOBALPROPERTYDEF gpd ON pd.GLOBAL_PROP_ID = gpd.OBJECT_ID " +
                "WHERE LOWER(pd.dbg_class_name) = LOWER(?) " +
                "ORDER BY cd.COLUMN_NAME";
        List<Map<String, Object>> props = new ArrayList<>();
        try (Connection conn = getFilenetDbConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, targetClass);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    String symName = rs.getString("SYMBOLIC_NAME");
                    String dispName = rs.getString("DISPLAY_NAME");
                    Object colTypeObj = rs.getObject("COL_DATATYPE_CODE");
                    if (colTypeObj == null) colTypeObj = rs.getObject("PROP_DATATYPE_CODE");
                    String dataType = mapColDatatypeCodeToName(colTypeObj);
                    String colSize = rs.getString("COL_SIZE");

                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("propertyName", symName != null ? symName : colName);
                    map.put("symbolicName", symName != null ? symName : colName);
                    map.put("displayName", dispName != null ? dispName : (symName != null ? symName : colName));
                    map.put("dataType", dataType);
                    map.put("length", colSize != null ? colSize : "");
                    props.add(map);
                }
            }
            log.info("[MAPPING] Retrieved {} target properties from FilenetDB for class: {}", props.size(), targetClass);
            if (!props.isEmpty()) return props;
        } catch (Exception e) {
            log.warn("[MAPPING] Could not query FilenetDB properties for target class {}: {}", targetClass, e.getMessage());
        }

        JsonNode root = loadTargetDocumentClassesJson();
        if (root != null) {
            JsonNode docClassesNode = root.get("documentClasses");
            if (docClassesNode != null && docClassesNode.isArray()) {
                for (JsonNode node : docClassesNode) {
                    String className = node.has("className") ? node.get("className").asText() : "";
                    if (className.equalsIgnoreCase(targetClass)) {
                        JsonNode propsNode = node.get("properties");
                        if (propsNode != null && propsNode.isArray()) {
                            List<Map<String, Object>> list = new ArrayList<>();
                            for (JsonNode pNode : propsNode) {
                                Map<String, Object> map = new LinkedHashMap<>();
                                String propName = pNode.has("propertyName") ? pNode.get("propertyName").asText() : "";
                                String dispName = pNode.has("displayName") ? pNode.get("displayName").asText() : propName;
                                String dataType = pNode.has("dataType") ? pNode.get("dataType").asText() : "STRING";
                                String length = pNode.has("length") ? pNode.get("length").asText() : "";

                                map.put("propertyName", propName);
                                map.put("symbolicName", propName);
                                map.put("displayName", dispName);
                                map.put("dataType", dataType);
                                map.put("length", length);
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

    private JsonNode loadTargetDocumentClassesJson() {
        File file = new File(targetDocumentClassesPath);
        if (file.exists() && file.length() > 0) {
            try {
                return objectMapper.readTree(file);
            } catch (Exception e) {
                log.warn("[MAPPING] Could not read target document classes file {}", file.getAbsolutePath(), e);
            }
        }
        try {
            ClassPathResource resource = new ClassPathResource("config/target-document-classes.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return objectMapper.readTree(is);
                }
            }
        } catch (Exception e) {
            log.warn("[MAPPING] Could not load target-document-classes from classpath", e);
        }
        return null;
    }

    public List<String> getDocumentClasses(String type) {
        if ("target".equalsIgnoreCase(type)) {
            return getTargetDocumentClasses();
        } else {
            return getFilenetDbSourceClasses();
        }
    }

    public List<Map<String, Object>> getClassProperties(String docClass, String type) {
        if ("target".equalsIgnoreCase(type)) {
            return getTargetClassProperties(docClass);
        } else {
            List<Map<String, Object>> dbProps = getFilenetDbSourceClassProperties(docClass);
            if (!dbProps.isEmpty()) return dbProps;

            // Fallback to local definition if FilenetDB query returns empty
            return getFallbackSourceClassProperties(docClass);
        }
    }

    private List<Map<String, Object>> getFallbackSourceClassProperties(String docClass) {
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
                            List<Map<String, Object>> list = new ArrayList<>();
                            for (JsonNode pNode : propsNode) {
                                Map<String, Object> map = new LinkedHashMap<>();
                                String srcProp = pNode.has("sourceProperty") ? pNode.get("sourceProperty").asText() : "";
                                String srcSym = pNode.has("sourceSymbolicName") ? pNode.get("sourceSymbolicName").asText() : srcProp;
                                String srcDt = pNode.has("sourceDataType") ? pNode.get("sourceDataType").asText() : "VARCHAR";
                                map.put("propertyName", srcProp);
                                map.put("symbolicName", srcSym);
                                map.put("dataType", srcDt);
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
}
