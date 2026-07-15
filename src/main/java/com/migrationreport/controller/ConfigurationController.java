package com.migrationreport.controller;

import com.migrationreport.dto.config.TenantConfig;
import com.migrationreport.service.ConfigurationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/config")
public class ConfigurationController {

    private final ConfigurationService configurationService;

    public ConfigurationController(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping
    public ResponseEntity<TenantConfig> getConfig() {
        log.info("[CONFIG] Fetching cached tenant configuration");
        return ResponseEntity.ok(configurationService.getCachedConfig());
    }

    @PostMapping
    public ResponseEntity<TenantConfig> saveConfig(@RequestBody TenantConfig config) {
        log.info("[CONFIG] Saving new tenant configuration. Apps count: {}", config.getApplications() != null ? config.getApplications().size() : 0);
        return ResponseEntity.ok(configurationService.saveConfig(config));
    }

    @GetMapping("/db-metadata")
    public ResponseEntity<Map<String, List<String>>> getDatabaseMetadata(@RequestParam("schema") String schema) {
        log.debug("[CONFIG] Fetching DB metadata for schema: '{}'", schema);
        return ResponseEntity.ok(configurationService.getDatabaseMetadata(schema));
    }
}
