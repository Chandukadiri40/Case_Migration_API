package com.db_search.controller;

import com.db_search.dto.DeliverableRequest;
import com.db_search.dto.DeliverableRowDTO;
import com.db_search.service.DeliverableService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deliverables")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class DeliverableController {

    private final DeliverableService deliverableService;

    public DeliverableController(DeliverableService deliverableService) {
        this.deliverableService = deliverableService;
    }

    @PostMapping("/migration-report")
    public ResponseEntity<List<java.util.Map<String, Object>>> getMigrationReport(
            @RequestBody DeliverableRequest request) {
        return ResponseEntity.ok(deliverableService.getMigrationReport(request));
    }
}