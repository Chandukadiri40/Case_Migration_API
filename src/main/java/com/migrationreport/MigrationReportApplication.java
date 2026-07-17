package com.migrationreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Relying on Spring Boot auto-configuration for DataSource and JPA
@SpringBootApplication
public class MigrationReportApplication {
    public static void main(String[] args) {
        SpringApplication.run(MigrationReportApplication.class, args);
    }
}
