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
        
        String url = defaultUrl;
        String username = defaultUsername;
        String password = defaultPassword;
        String driver = defaultDriver;

        if (dbConfigFile.exists()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                DbConfigWrapper wrapper = mapper.readValue(dbConfigFile, DbConfigWrapper.class);
                
                if (wrapper.getDatabases() != null && wrapper.getActiveDatabaseType() != null) {
                    Map<String, String> activeDbConfig = null;
                    for (Map<String, String> db : wrapper.getDatabases()) {
                        if (wrapper.getActiveDatabaseType().equalsIgnoreCase(db.get("databaseType"))) {
                            activeDbConfig = db;
                            break;
                        }
                    }
                    
                    if (activeDbConfig != null) {
                        if (activeDbConfig.containsKey(KEY_URL) && !activeDbConfig.get(KEY_URL).trim().isEmpty()) {
                            url = activeDbConfig.get(KEY_URL);
                        }
                        if (activeDbConfig.containsKey(KEY_USERNAME) && !activeDbConfig.get(KEY_USERNAME).trim().isEmpty()) {
                            username = activeDbConfig.get(KEY_USERNAME);
                        }
                        if (activeDbConfig.containsKey(KEY_PASSWORD) && !activeDbConfig.get(KEY_PASSWORD).trim().isEmpty()) {
                            password = EncryptionUtil.decrypt(activeDbConfig.get(KEY_PASSWORD));
                        }
                        if (activeDbConfig.containsKey(KEY_DRIVER) && !activeDbConfig.get(KEY_DRIVER).trim().isEmpty()) {
                            driver = activeDbConfig.get(KEY_DRIVER);
                        }
                        log.info("[CONFIG] Loaded database configuration for active type {} from {}", wrapper.getActiveDatabaseType(), dbConfigFile.getAbsolutePath());
                    } else {
                        log.warn("[CONFIG] Active database type {} not found in db-config.json, using fallback properties.", wrapper.getActiveDatabaseType());
                    }
                }
            } catch (Exception e) {
                log.error("[CONFIG] Failed to load db-config.json, falling back to application.properties. Error: {}", e.getMessage());
            }
        } else {
            log.info("[CONFIG] No db-config.json found, using default application.properties");
        }

        // If default password starts with ENC(, it means it was encrypted by Jasypt and we don't have decrypt logic here.
        // But for our new AES logic, we decrypt it above.
        

        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driver)
                .build();
    }
}
