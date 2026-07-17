package com.migrationreport.dialect;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.Map;
import com.migrationreport.dto.config.DbConfigWrapper;

@Slf4j
@Configuration
public class DialectConfig {

    @Value("${app.database.type:postgres}")
    private String defaultDatabaseType;

    @Value("${db.config.file.path:config/db-config.json}")
    private String dbConfigFilePath;

    @Bean
    public SqlDialect sqlDialect() {
        String databaseType = defaultDatabaseType;
        File dbConfigFile = new File(dbConfigFilePath);
        
        if (dbConfigFile.exists()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                DbConfigWrapper wrapper = mapper.readValue(dbConfigFile, DbConfigWrapper.class);
                if (wrapper.getActiveDatabaseType() != null && !wrapper.getActiveDatabaseType().trim().isEmpty()) {
                    databaseType = wrapper.getActiveDatabaseType();
                }
            } catch (Exception e) {
                log.error("[DIALECT] Failed to load db-config.json, falling back to application.properties. Error: {}", e.getMessage());
            }
        }

        if ("mssql".equalsIgnoreCase(databaseType)) {
            log.info("[DIALECT] Initializing MSSQL Dialect provider...");
            return new SqlServerDialect();
        } else {
            log.info("[DIALECT] Initializing PostgreSQL Dialect provider...");
            return new PostgresDialect();
        }
    }
}
