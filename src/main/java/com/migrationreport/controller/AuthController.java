package com.migrationreport.controller;

import com.migrationreport.dto.LoginRequest;
import com.migrationreport.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/auth")

public class AuthController {

    private static final String KEY_STATUS = "status";
    private static final String KEY_MESSAGE = "message";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates a user using credentials stored in the configured database.
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        log.info("Starting method: login with arguments: username={}", request.getUsername());
        boolean authenticated = authService.authenticate(request.getUsername(), request.getPassword());
        
        Map<String, Object> response = new HashMap<>();
        if (authenticated) {
            response.put(KEY_STATUS, "success");
            response.put(KEY_MESSAGE, "Login successful");
            response.put("username", request.getUsername());
            response.put("name", request.getUsername());   // frontend uses 'name' for display
            response.put("role", "User");                  // default role; extend as needed
            response.put("token", "session-token-" + request.getUsername());
            log.info("Ending method: login (Success)");
            return ResponseEntity.ok(response);
        } else {
            response.put(KEY_STATUS, "fail");
            response.put(KEY_MESSAGE, "Invalid username or password");
            log.info("Ending method: login (Fail)");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    /**
     * Registers a new user.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody LoginRequest request) {
        log.info("Starting method: register with arguments: username={}", request.getUsername());
        boolean registered = authService.register(request.getUsername(), request.getPassword());
        
        Map<String, Object> response = new HashMap<>();
        if (registered) {
            response.put(KEY_STATUS, "success");
            response.put(KEY_MESSAGE, "Registration successful");
            log.info("Ending method: register (Success)");
            return ResponseEntity.ok(response);
        } else {
            response.put(KEY_STATUS, "fail");
            response.put(KEY_MESSAGE, "Registration failed. Username might already exist or input is invalid.");
            log.info("Ending method: register (Fail)");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
