package com.db_search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

// Exclude DataSourceAutoConfiguration to handle dataSource creation manually via custom configuration
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class DbSearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(DbSearchApplication.class, args);
    }
}
