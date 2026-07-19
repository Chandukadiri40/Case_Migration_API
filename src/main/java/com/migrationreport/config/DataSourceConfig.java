package com.migrationreport.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.File;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import com.migrationreport.dto.config.DbConfigWrapper;
import com.migrationreport.util.EncryptionUtil;

@Slf4j
@Configuration
public class DataSourceConfig {

    private static final String KEY_URL = "url";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_DRIVER = "driver";

    @Value("${db.config.file.path:config/db-config.json}")
    private String dbConfigFilePath;

    @Value("${spring.datasource.url:}")
    private String defaultUrl;

    @Value("${spring.datasource.username:}")
    private String defaultUsername;

    @Value("${spring.datasource.password:}")
    private String defaultPassword;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String defaultDriver;

    @Bean
    @Primary
    public DataSource dataSource() {
        File dbConfigFile = new File(dbConfigFilePath);
        
        String[] credentials = {defaultUrl, defaultUsername, defaultPassword, defaultDriver};

        if (dbConfigFile.exists()) {
            loadConfigFromFile(dbConfigFile, credentials);
        } else {
            log.info("[CONFIG] No db-config.json found, using default application.properties");
        }

        return DataSourceBuilder.create()
                .url(credentials[0])
                .username(credentials[1])
                .password(credentials[2])
                .driverClassName(credentials[3])
                .build();
    }

    private void loadConfigFromFile(File dbConfigFile, String[] credentials) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            DbConfigWrapper wrapper = mapper.readValue(dbConfigFile, DbConfigWrapper.class);
            
            if (wrapper.getDatabases() != null && wrapper.getActiveDatabaseType() != null) {
                Map<String, String> activeDbConfig = findActiveDbConfig(wrapper);
                
                if (activeDbConfig != null && !activeDbConfig.isEmpty()) {
                    applyActiveConfig(activeDbConfig, credentials);
                    log.info("[CONFIG] Loaded database configuration for active type {} from {}", wrapper.getActiveDatabaseType(), dbConfigFile.getAbsolutePath());
                } else {
                    log.warn("[CONFIG] Active database type {} not found in db-config.json, using fallback properties.", wrapper.getActiveDatabaseType());
                }
            }
        } catch (Exception e) {
            log.error("[CONFIG] Failed to load db-config.json, falling back to application.properties. Error: {}", e.getMessage());
        }
    }

    private Map<String, String> findActiveDbConfig(DbConfigWrapper wrapper) {
        for (Map<String, String> db : wrapper.getDatabases()) {
            if (wrapper.getActiveDatabaseType().equalsIgnoreCase(db.get("databaseType"))) {
                return db;
            }
        }
        return java.util.Collections.emptyMap();
    }

    private void applyActiveConfig(Map<String, String> activeDbConfig, String[] credentials) {
        if (isValidValue(activeDbConfig, KEY_URL)) {
            credentials[0] = activeDbConfig.get(KEY_URL);
        }
        if (isValidValue(activeDbConfig, KEY_USERNAME)) {
            credentials[1] = activeDbConfig.get(KEY_USERNAME);
        }
        if (isValidValue(activeDbConfig, KEY_PASSWORD)) {
            credentials[2] = EncryptionUtil.decrypt(activeDbConfig.get(KEY_PASSWORD));
        }
        if (isValidValue(activeDbConfig, KEY_DRIVER)) {
            credentials[3] = activeDbConfig.get(KEY_DRIVER);
        }
    }

    private boolean isValidValue(Map<String, String> config, String key) {
        return config.containsKey(key) && !config.get(key).trim().isEmpty();
    }
}
