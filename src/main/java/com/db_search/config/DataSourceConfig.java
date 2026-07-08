package com.db_search.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Value("${db.type}")
    private String dbType;

    @Value("${db.url}")
    private String dbUrl;

    @Value("${db.username}")
    private String dbUsername;

    @Value("${db.password}")
    private String dbPassword;

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(dbUrl);
        dataSource.setUsername(dbUsername);
        dataSource.setPassword(dbPassword);

        String driverClassName = getDriverClassName(dbType);
        dataSource.setDriverClassName(driverClassName);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private String getDriverClassName(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("db.type property must be specified in application.properties");
        }
        switch (type.toLowerCase().trim()) {
            case "postgres":
            case "postgresql":
                return "org.postgresql.Driver";
            case "mssql":
            case "sqlserver":
                return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "db2":
                return "com.ibm.db2.jcc.DB2Driver";
            default:
                throw new IllegalArgumentException("Unsupported database type: " + type + ". Supported types are: postgres, mssql, db2");
        }
    }
}
