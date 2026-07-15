package com.migrationreport.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathValidator {
    
    public static Path validateAndNormalize(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be empty.");
        }
        
        // Prevent directory traversal attacks
        if (pathStr.contains("..") || pathStr.contains("\0")) {
            throw new IllegalArgumentException("Invalid characters in path.");
        }
        
        try {
            Path path = Paths.get(pathStr).normalize().toAbsolutePath();
            
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

