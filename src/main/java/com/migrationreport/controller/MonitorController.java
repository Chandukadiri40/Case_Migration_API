package com.migrationreport.controller;

import com.migrationreport.dto.LogConfigDTO;
import com.migrationreport.dto.LogEntryDTO;
import com.migrationreport.service.MonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/monitor")
@CrossOrigin(origins = "*")
public class MonitorController {

    private final MonitorService monitorService;

    @Autowired
    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @PostMapping("/config")
    public ResponseEntity<LogConfigDTO> saveConfig(@RequestBody LogConfigDTO configDTO) {
        return ResponseEntity.ok(monitorService.saveConfig(configDTO));
    }

    @GetMapping("/config")
    public ResponseEntity<LogConfigDTO> getConfig() {
        LogConfigDTO config = monitorService.getConfig();
        if (config == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(config);
    }

    @GetMapping("/logs/dates")
    public ResponseEntity<List<String>> getLogDates() {
        return ResponseEntity.ok(monitorService.getAvailableDates());
    }

    @GetMapping("/logs")
    public ResponseEntity<List<LogEntryDTO>> getLogs(@RequestParam String date) {
        return ResponseEntity.ok(monitorService.getLogsByDate(date));
    }
}

