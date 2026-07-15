package com.migrationreport.dialect;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class DialectConfig {

    @Value("${app.database.type:postgres}")
    private String databaseType;

    @Bean
    public SqlDialect sqlDialect() {
        if ("mssql".equalsIgnoreCase(databaseType)) {
            log.info("[DIALECT] Initializing MSSQL Dialect provider...");
            return new SqlServerDialect();
        } else {
            log.info("[DIALECT] Initializing PostgreSQL Dialect provider...");
            return new PostgresDialect();
        }
    }
}
