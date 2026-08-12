package com.migrationreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import lombok.extern.slf4j.Slf4j;

// Relying on Spring Boot auto-configuration for DataSource and JPA
@Slf4j
@SpringBootApplication
public class MigrationReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(MigrationReportApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logApplicationStartup() {
        log.info("==========Application Started ========");
    }
}
