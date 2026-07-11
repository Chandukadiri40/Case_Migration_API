package com.db_search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

// Relying on Spring Boot auto-configuration for DataSource and JPA
@SpringBootApplication
public class DbSearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(DbSearchApplication.class, args);
    }
}
