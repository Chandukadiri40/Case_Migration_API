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
import com.migrationreport.dto.config.DbConfigWrapper;
import java.sql.Connection;
import com.migrationreport.util.EncryptionUtil;
import java.sql.DriverManager;

@Slf4j
@Service
public class ConfigurationService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;

    @Value("${config.file.path:config/tenant-mappings.json}")
    private String configFilePath;

    @Value("${db.config.file.path:config/db-config.json}")
    private String dbConfigFilePath;

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
        String query = "SELECT table_name, column_name FROM information_schema.columns WHERE LOWER(table_schema) = LOWER(?)";
        
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query, schemaName);
        
        Map<String, List<String>> metadata = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String tableName = (String) row.get("table_name");
            String columnName = (String) row.get("column_name");
            
            metadata.computeIfAbsent(tableName, k -> new ArrayList<>()).add(columnName);
        }
        return metadata;
    }

    public DbConfigWrapper getDbConfig() {
        File dbConfigFile = new File(dbConfigFilePath);
        if (!dbConfigFile.exists()) {
            DbConfigWrapper empty = new DbConfigWrapper();
            empty.setDatabases(new ArrayList<>());
            return empty;
        }
        try {
            DbConfigWrapper wrapper = objectMapper.readValue(dbConfigFile, DbConfigWrapper.class);
            if (wrapper.getDatabases() != null) {
                for (Map<String, String> dbConfig : wrapper.getDatabases()) {
                    if (dbConfig.containsKey("password") && dbConfig.get("password") != null) {
                        dbConfig.put("password", EncryptionUtil.decrypt(dbConfig.get("password")));
                    }
                }
            } else {
                wrapper.setDatabases(new ArrayList<>());
            }
            return wrapper;
        } catch (IOException e) {
            log.error("[CONFIG] Failed to load DB config file: {}", e.getMessage());
            DbConfigWrapper empty = new DbConfigWrapper();
            empty.setDatabases(new ArrayList<>());
            return empty;
        }
    }

    public DbConfigWrapper saveDbConfig(DbConfigWrapper wrapper) {
        try {
            File dbConfigFile = new File(dbConfigFilePath);
            File parent = dbConfigFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            DbConfigWrapper configToSave = new DbConfigWrapper();
            configToSave.setActiveDatabaseType(wrapper.getActiveDatabaseType());
            List<Map<String, String>> dbsToSave = new ArrayList<>();
            
            if (wrapper.getDatabases() != null) {
                for (Map<String, String> db : wrapper.getDatabases()) {
                    Map<String, String> configMap = new HashMap<>(db);
                    if (configMap.containsKey("password") && configMap.get("password") != null && !configMap.get("password").trim().isEmpty()) {
                        configMap.put("password", EncryptionUtil.encrypt(configMap.get("password")));
                    }
                    dbsToSave.add(configMap);
                }
            }
            configToSave.setDatabases(dbsToSave);
            
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dbConfigFile, configToSave);
            log.info("[CONFIG] Database Configuration successfully saved to {}", dbConfigFile.getAbsolutePath());
            return configToSave;
        } catch (IOException e) {
            log.error("[CONFIG] Error saving DB configuration file: {}", e.getMessage());
            throw new ConfigurationException("Failed to save DB configuration to disk: " + e.getMessage());
        }
    }

    public boolean testDbConnection(Map<String, String> dbConfig) {
        String url = dbConfig.get("url");
        String username = dbConfig.get("username");
        String password = dbConfig.get("password");
        String driver = dbConfig.get("driver");
        
        if (url == null || username == null || password == null || driver == null) {
            return false;
        }
        
        try {
            Class.forName(driver);
            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                return conn.isValid(5);
            }
        } catch (Exception e) {
            log.error("[CONFIG] Test connection failed: {}", e.getMessage());
            return false;
        }
    }
}
