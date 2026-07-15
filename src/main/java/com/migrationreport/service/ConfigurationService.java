package com.migrationreport.service;

import com.migrationreport.dto.config.TenantConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import com.migrationreport.exception.ConfigurationException;

@Slf4j
@Service
public class ConfigurationService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;

    @Value("${config.file.path:config/tenant-mappings.json}")
    private String configFilePath;

    private TenantConfig cachedConfig;

    public ConfigurationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        loadConfig();
    }

    public synchronized TenantConfig loadConfig() {
        File configFile = new File(configFilePath);
        if (!configFile.exists()) {
            cachedConfig = new TenantConfig();
            cachedConfig.setApplications(new ArrayList<>());
            return cachedConfig;
        }

        try {
            log.info("[CONFIG] Loading tenant configuration from {}", configFile.getAbsolutePath());
            cachedConfig = objectMapper.readValue(configFile, TenantConfig.class);
            if (cachedConfig.getApplications() == null) {
                cachedConfig.setApplications(new ArrayList<>());
            }
            log.info("[CONFIG] Loaded {} applications from config.", cachedConfig.getApplications().size());
        } catch (IOException e) {
            log.error("[CONFIG] Failed to load config file: {}", e.getMessage());
            cachedConfig = new TenantConfig();
            cachedConfig.setApplications(new ArrayList<>());
        }
        return cachedConfig;
    }

    public synchronized TenantConfig saveConfig(TenantConfig config) {
        try {
            File configFile = new File(configFilePath);
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config);
            this.cachedConfig = config;
            log.info("[CONFIG] Configuration successfully saved to {}", configFile.getAbsolutePath());
            return this.cachedConfig;
        } catch (IOException e) {
            log.error("[CONFIG] Error saving configuration file: {}", e.getMessage());
            throw new ConfigurationException("Failed to save configuration to disk: " + e.getMessage());
        }
    }

    public TenantConfig getCachedConfig() {
        if (cachedConfig == null) {
            return loadConfig();
        }
        return cachedConfig;
    }

    public TenantConfig.ApplicationConfig getApplicationConfig(String appId) {
        TenantConfig config = getCachedConfig();
        if (config == null || config.getApplications() == null) return null;
        return config.getApplications().stream()
                .filter(app -> app.getAppId().equalsIgnoreCase(appId))
                .findFirst()
                .orElse(null);
    }

    public String getSystemColumn(String appId, String columnKey, String defaultValue) {
        if (appId != null) {
            TenantConfig.ApplicationConfig appConfig = getApplicationConfig(appId);
            if (appConfig != null && appConfig.getSystemColumns() != null) {
                String val = appConfig.getSystemColumns().get(columnKey);
                if (val != null && !val.trim().isEmpty()) {
                    return val.trim();
                }
            }
        }
        return defaultValue;
    }

    public Map<String, List<String>> getDatabaseMetadata(String schemaName) {
        String query = "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = ?";
        
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query, schemaName);
        
        Map<String, List<String>> metadata = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String tableName = (String) row.get("table_name");
            String columnName = (String) row.get("column_name");
            
            metadata.computeIfAbsent(tableName, k -> new ArrayList<>()).add(columnName);
        }
        return metadata;
    }
}
