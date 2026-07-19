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
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Slf4j
@Service
public class ConfigurationService {

    private static final String PASSWORD = "password";

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

    public String getSystemTable(String appId, String tableKey, String defaultName) {
        if (appId != null) {
            TenantConfig.ApplicationConfig appConfig = getApplicationConfig(appId);
            if (appConfig != null && appConfig.getClassifiedTables() != null) {
                List<String> tables = appConfig.getClassifiedTables().get(tableKey);
                if (tables != null && !tables.isEmpty()) {
                    return tables.get(0);
                }
            }
        }
        return defaultName;
    }


    public Map<String, List<String>> getDatabaseMetadata(String schemaName) {
        DbConfigWrapper dbConfig = getDbConfig();
        if (dbConfig != null && dbConfig.getDatabases() != null && !dbConfig.getDatabases().isEmpty()) {
            Map<String, String> dbProps = dbConfig.getDatabases().get(0);
            String url = dbProps.get("url");
            String username = dbProps.get("username");
            String password = dbProps.get(PASSWORD);
            String driver = dbProps.get("driver");
            
            if (url != null && username != null && password != null && driver != null) {
                try {
                    Class.forName(driver);
                    try (Connection conn = DriverManager.getConnection(url, username, password)) {
                        String dynamicQuery = "SELECT table_name, column_name FROM information_schema.columns WHERE LOWER(table_schema) = LOWER(?)";
                        try (PreparedStatement pstmt = conn.prepareStatement(dynamicQuery)) {
                            pstmt.setString(1, schemaName);
                            try (ResultSet rs = pstmt.executeQuery()) {
                                Map<String, List<String>> metadata = new HashMap<>();
                                while (rs.next()) {
                                    String tableName = rs.getString("table_name");
                                    String columnName = rs.getString("column_name");
                                    metadata.computeIfAbsent(tableName, k -> new ArrayList<>()).add(columnName);
                                }
                                log.info("[CONFIG] Successfully fetched database metadata from configured dynamic database for schema: {}", schemaName);
                                return metadata;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[CONFIG] Failed to fetch metadata from dynamic DB: {}. Falling back to internal DB.", e.getMessage());
                }
            }
        }

        // Fallback to internal application database
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
                    if (dbConfig.containsKey(PASSWORD) && dbConfig.get(PASSWORD) != null) {
                        dbConfig.put(PASSWORD, EncryptionUtil.decrypt(dbConfig.get(PASSWORD)));
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
            
            // Load existing config to preserve untouched passwords
            DbConfigWrapper existingConfig = null;
            if (dbConfigFile.exists()) {
                existingConfig = objectMapper.readValue(dbConfigFile, DbConfigWrapper.class);
            }
            
            DbConfigWrapper configToSave = new DbConfigWrapper();
            configToSave.setActiveDatabaseType(wrapper.getActiveDatabaseType());
            List<Map<String, String>> dbsToSave = new ArrayList<>();
            
            if (wrapper.getDatabases() != null) {
                for (Map<String, String> db : wrapper.getDatabases()) {
                    Map<String, String> configMap = new HashMap<>(db);
                    String incPwd = configMap.get(PASSWORD);
                    
                    if (incPwd == null || incPwd.trim().isEmpty() || "********".equals(incPwd)) {
                        // Restore old encrypted password from disk
                        if (existingConfig != null && existingConfig.getDatabases() != null) {
                            for (Map<String, String> oldDb : existingConfig.getDatabases()) {
                                if (oldDb.get("databaseType").equals(configMap.get("databaseType"))) {
                                    if (oldDb.containsKey(PASSWORD)) {
                                        configMap.put(PASSWORD, oldDb.get(PASSWORD));
                                    }
                                    break;
                                }
                            }
                        }
                    } else {
                        // Encrypt new password
                        configMap.put(PASSWORD, EncryptionUtil.encrypt(incPwd));
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
        String password = dbConfig.get(PASSWORD);
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
