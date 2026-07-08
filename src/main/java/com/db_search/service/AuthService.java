package com.db_search.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final String usersTable;
    private final String usernameColumn;
    private final String passwordColumn;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            @Value("${auth.users-table}") String usersTable,
            @Value("${auth.username-column}") String usernameColumn,
            @Value("${auth.password-column}") String passwordColumn) {
        this.jdbcTemplate = jdbcTemplate;
        this.usersTable = usersTable;
        this.usernameColumn = usernameColumn;
        this.passwordColumn = passwordColumn;
    }

    /**
     * Authenticates the user by checking credentials in the configured database.
     */
    public boolean authenticate(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }

        // Table and column names are loaded from configuration (safe from injection).
        // The user input (username) is parameterized using the '?' placeholder to prevent SQL injection.
        String sql = String.format("SELECT %s FROM %s WHERE %s = ?", passwordColumn, usersTable, usernameColumn);

        try {
            String dbPassword = jdbcTemplate.queryForObject(sql, String.class, username.trim());
            
            // Checks if the passwords match.
            // Note: If you store passwords as BCrypt hashes in the database, 
            // you would replace this with: passwordEncoder.matches(password, dbPassword)
            return password.equals(dbPassword);
        } catch (EmptyResultDataAccessException e) {
            // User does not exist in the database
            return false;
        }
    }
}
