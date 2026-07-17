package com.migrationreport.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathValidator {
    
    private PathValidator() {
        // Hide implicit public constructor
    }
    
    public static Path validateAndNormalize(String pathStr, String allowedBasePathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be empty.");
        }
        
        // Prevent directory traversal attacks and validate characters
        if (pathStr.contains("..") || pathStr.contains("\0")) {
            throw new IllegalArgumentException("Invalid characters in path.");
        }
        
        if (!pathStr.matches("^[a-zA-Z0-9:\\\\/\\._\\- ]+$")) {
            throw new IllegalArgumentException("Path contains illegal characters.");
        }
        
        try {
            Path path = Paths.get(pathStr).normalize().toAbsolutePath();
            Path allowedBasePath = Paths.get(allowedBasePathStr).normalize().toAbsolutePath();
            
            if (!path.startsWith(allowedBasePath)) {
                throw new IllegalArgumentException("Path is outside the allowed directory.");
            }
            
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Directory does not exist.");
            }
            if (!Files.isDirectory(path)) {
                throw new IllegalArgumentException("Path is not a directory.");
            }
            if (!Files.isReadable(path)) {
                throw new IllegalArgumentException("Directory is not readable.");
            }
            
            return path;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid path format.");
        }
    }
}

