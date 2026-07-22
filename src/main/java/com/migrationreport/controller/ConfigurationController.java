package com.migrationreport.controller;

import com.migrationreport.dto.config.TenantConfig;
import com.migrationreport.service.ConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.migrationreport.dto.config.DbConfigWrapper;
import org.springframework.beans.factory.annotation.Value;

import lombok.extern.slf4j.Slf4j;

@Slf4j

@RestController
@RequestMapping("/api/config")
public class ConfigurationController {

    private final ConfigurationService configurationService;

    public ConfigurationController(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping
    public ResponseEntity<TenantConfig> getConfig() {
        log.info("Starting method: getConfig");
        log.info("[CONFIG] Fetching cached tenant configuration");
        ResponseEntity<TenantConfig> result = ResponseEntity.ok(configurationService.getCachedConfig());
        log.info("Ending method: getConfig");
        return result;
    }

    @PostMapping
    public ResponseEntity<TenantConfig> saveConfig(@RequestBody TenantConfig config) {
        log.info("Starting method: saveConfig with arguments: config={}", config);
        log.info("[CONFIG] Saving new tenant configuration. Apps count: {}", config.getApplications() != null ? config.getApplications().size() : 0);
        ResponseEntity<TenantConfig> result = ResponseEntity.ok(configurationService.saveConfig(config));
        log.info("Ending method: saveConfig");
        return result;
    }

    private static final String PASSWORD_KEY = "password";

    @GetMapping("/db")
    public ResponseEntity<DbConfigWrapper> getDbConfig() {
        log.info("Starting method: getDbConfig");
        log.info("[CONFIG] Fetching Database Configuration");
        DbConfigWrapper wrapper = configurationService.getDbConfig();
        if (wrapper != null && wrapper.getDatabases() != null) {
            for (Map<String, String> db : wrapper.getDatabases()) {
                if (db.containsKey(PASSWORD_KEY) && db.get(PASSWORD_KEY) != null && !db.get(PASSWORD_KEY).isEmpty()) {
                    db.put(PASSWORD_KEY, "********");
                }
            }
        }
        ResponseEntity<DbConfigWrapper> result = ResponseEntity.ok(wrapper);
        log.info("Ending method: getDbConfig");
        return result;
    }

    @PostMapping("/db")
    public ResponseEntity<DbConfigWrapper> saveDbConfig(@RequestBody DbConfigWrapper dbConfig) {
        log.info("Starting method: saveDbConfig with arguments: dbConfig={}", dbConfig);
        log.info("[CONFIG] Saving new Database Configuration");
        ResponseEntity<DbConfigWrapper> result = ResponseEntity.ok(configurationService.saveDbConfig(dbConfig));
        log.info("Ending method: saveDbConfig");
        return result;
    }

    @PostMapping("/db/test")
    public ResponseEntity<Map<String, Object>> testDbConnection(@RequestBody Map<String, String> dbConfig) {
        log.info("Starting method: testDbConnection with arguments: dbConfig={}", dbConfig);
        log.info("[CONFIG] Testing Database Connection");
        boolean success = configurationService.testDbConnection(dbConfig);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (success) {
            response.put("message", "Connection successful!");
        } else {
            response.put("message", "Connection failed. Please check credentials.");
        }
        log.info("Ending method: testDbConnection");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/db-metadata")
    public ResponseEntity<Map<String, List<String>>> getDatabaseMetadata(@RequestParam("schema") String schema) {
        log.info("Starting method: getDatabaseMetadata with arguments: schema={}", schema);
        log.debug("[CONFIG] Fetching DB metadata for schema: '{}'", schema);
        ResponseEntity<Map<String, List<String>>> result = ResponseEntity.ok(configurationService.getDatabaseMetadata(schema));
        log.info("Ending method: getDatabaseMetadata");
        return result;
    }

    @Value("${exceptions.search.max-date-range-months:3}")
    private int maxDateRangeMonths;

    @Value("${app.filenet.fixed-mapping:false}")
    private boolean fixedFilenetMapping;

    @GetMapping("/ui-settings")
    public ResponseEntity<Map<String, Object>> getUiSettings() {
        log.info("Starting method: getUiSettings");
        Map<String, Object> settings = new HashMap<>();
        settings.put("maxDateRangeMonths", maxDateRangeMonths);
        settings.put("fixedFilenetMapping", fixedFilenetMapping);
        log.info("Ending method: getUiSettings");
        return ResponseEntity.ok(settings);
    }
}
