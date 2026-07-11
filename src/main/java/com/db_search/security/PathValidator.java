package com.db_search.security;

import com.db_search.exception.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathValidator {
    
    public static Path validateAndNormalize(String pathStr) {
        if (pathStr == null || pathStr.trim().isEmpty()) {
            throw new InvalidPathException("Path cannot be empty.");
        }
        
        // Prevent directory traversal attacks
        if (pathStr.contains("..") || pathStr.contains("\0")) {
            throw new InvalidPathException("Invalid characters in path.");
        }
        
        try {
            Path path = Paths.get(pathStr).normalize().toAbsolutePath();
            
            if (!Files.exists(path)) {
                throw new InvalidPathException("Directory does not exist.");
            }
            if (!Files.isDirectory(path)) {
                throw new InvalidPathException("Path is not a directory.");
            }
            if (!Files.isReadable(path)) {
                throw new InvalidPathException("Directory is not readable.");
            }
            
            return path;
        } catch (InvalidPathException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidPathException("Invalid path format.");
        }
    }
}

