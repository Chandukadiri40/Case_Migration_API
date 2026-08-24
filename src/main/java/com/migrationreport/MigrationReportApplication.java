package com.migrationreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

// Relying on Spring Boot auto-configuration for DataSource and JPA
@Slf4j
@EnableCaching
@SpringBootApplication
public class MigrationReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(MigrationReportApplication.class, args);
    }

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("discovery-classes", "discovery-properties", "discovery-reports");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logApplicationStartup() {
        log.info("==========Application Started ========");
    }
}
