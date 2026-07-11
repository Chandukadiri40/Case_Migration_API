package com.db_search.controller;

import com.db_search.dto.LoginRequest;
import com.db_search.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates a user using credentials stored in the configured database.
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        boolean authenticated = authService.authenticate(request.getUsername(), request.getPassword());
        
        Map<String, Object> response = new HashMap<>();
        if (authenticated) {
            response.put("status", "success");
            response.put("message", "Login successful");
            response.put("username", request.getUsername());
            response.put("name", request.getUsername());   // frontend uses 'name' for display
            response.put("role", "User");                  // default role; extend as needed
            response.put("token", "session-token-" + request.getUsername());
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "fail");
            response.put("message", "Invalid username or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }
}
