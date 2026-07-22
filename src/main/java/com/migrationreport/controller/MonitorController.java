package com.migrationreport.controller;

import com.migrationreport.dto.LogConfigDTO;
import com.migrationreport.dto.LogEntryDTO;
import com.migrationreport.service.MonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/monitor")

public class MonitorController {

    private final MonitorService monitorService;

    @Autowired
    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @PostMapping("/config")
    public ResponseEntity<LogConfigDTO> saveConfig(@RequestBody LogConfigDTO configDTO) {
        log.info("Starting method: saveConfig with arguments: configDTO={}", configDTO);
        ResponseEntity<LogConfigDTO> result = ResponseEntity.ok(monitorService.saveConfig(configDTO));
        log.info("Ending method: saveConfig");
        return result;
    }

    @GetMapping("/config")
    public ResponseEntity<LogConfigDTO> getConfig() {
        log.info("Starting method: getConfig");
        LogConfigDTO config = monitorService.getConfig();
        if (config == null) {
            log.info("Ending method: getConfig");
            return ResponseEntity.noContent().build();
        }
        log.info("Ending method: getConfig");
        return ResponseEntity.ok(config);
    }

    @GetMapping("/logs/dates")
    public ResponseEntity<List<String>> getLogDates() {
        log.info("Starting method: getLogDates");
        ResponseEntity<List<String>> result = ResponseEntity.ok(monitorService.getAvailableDates());
        log.info("Ending method: getLogDates");
        return result;
    }

    @GetMapping("/logs")
    public ResponseEntity<List<LogEntryDTO>> getLogs(@RequestParam String date) {
        log.info("Starting method: getLogs with arguments: date={}", date);
        ResponseEntity<List<LogEntryDTO>> result = ResponseEntity.ok(monitorService.getLogsByDate(date));
        log.info("Ending method: getLogs");
        return result;
    }
}

