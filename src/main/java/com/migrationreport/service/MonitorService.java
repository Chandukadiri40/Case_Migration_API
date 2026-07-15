package com.migrationreport.service;

import com.migrationreport.dto.LogConfigDTO;
import com.migrationreport.dto.LogEntryDTO;
import com.migrationreport.security.PathValidator;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class MonitorService {

    private volatile LogConfigDTO currentConfig;

    private static final Pattern LOG_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?)\\s+([A-Z]+)\\s+(?:.*?)\\s+---\\s+\\[(.*?)\\]\\s+(.*?)\\s+:\\s+(.*)$");

    public LogConfigDTO saveConfig(LogConfigDTO config) {
        Path path = PathValidator.validateAndNormalize(config.getLogPath());
        LogConfigDTO validatedConfig = new LogConfigDTO(path.toString());
        this.currentConfig = validatedConfig;
        return validatedConfig;
    }

    public LogConfigDTO getConfig() {
        return this.currentConfig;
    }

    public List<String> getAvailableDates() {
        if (this.currentConfig == null) {
            return new ArrayList<>();
        }
        
        Path dirPath = PathValidator.validateAndNormalize(this.currentConfig.getLogPath());
        
        try (Stream<Path> paths = Files.list(dirPath)) {
            return paths
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(name -> name.toLowerCase().matches(".*\\d{4}-\\d{2}-\\d{2}\\.log$") || name.toLowerCase().endsWith(".log"))
                .map(this::extractDateFromFilename)
                .distinct()
                .sorted((a, b) -> b.compareTo(a))
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan log directory", e);
        }
    }

    public List<LogEntryDTO> getLogsByDate(String date) {
        if (this.currentConfig == null) {
            return new ArrayList<>();
        }
        
        Path dirPath = PathValidator.validateAndNormalize(this.currentConfig.getLogPath());
        
        if (date == null || date.contains("/") || date.contains("\\") || date.contains("..")) {
            throw new IllegalArgumentException("Invalid date format");
        }
        
        List<LogEntryDTO> logs = new ArrayList<>();
        try (Stream<Path> paths = Files.list(dirPath)) {
            Path targetFile = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().contains(date))
                .findFirst()
                .orElse(null);
                
            if (targetFile != null) {
                try (BufferedReader reader = new BufferedReader(new FileReader(targetFile.toFile()))) {
                    String line;
                    LogEntryDTO currentEntry = null;
                    StringBuilder messageBuilder = new StringBuilder();
                    Pattern customPattern = Pattern.compile("^(\\d{2}-\\d{2}-\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?\\s+[AP]M)\\s+-\\s+(.*)$");
                    
                    while ((line = reader.readLine()) != null) {
                        Matcher matcher = LOG_PATTERN.matcher(line);
                        Matcher customMatcher = customPattern.matcher(line);
                        if (matcher.matches()) {
                            if (currentEntry != null) {
                                currentEntry.setMessage(messageBuilder.toString().trim());
                                logs.add(currentEntry);
                            }
                            currentEntry = new LogEntryDTO();
                            currentEntry.setTimestamp(matcher.group(1));
                            currentEntry.setLevel(matcher.group(2));
                            currentEntry.setThread(matcher.group(3).trim());
                            currentEntry.setLogger(matcher.group(4).trim());
                            messageBuilder = new StringBuilder(matcher.group(5));
                        } else if (customMatcher.matches()) {
                            if (currentEntry != null) {
                                currentEntry.setMessage(messageBuilder.toString().trim());
                                logs.add(currentEntry);
                            }
                            currentEntry = new LogEntryDTO();
                            currentEntry.setTimestamp(customMatcher.group(1));
                            
                            String fullMsg = customMatcher.group(2);
                            String level = fullMsg.toLowerCase().startsWith("error") ? "ERROR" : "INFO";
                            currentEntry.setLevel(level);
                            currentEntry.setThread("");
                            
                            String logger = "AppLog";
                            String msg = fullMsg;
                            int idx = fullMsg.indexOf(" ====:");
                            if (idx != -1) {
                                logger = fullMsg.substring(idx + 6);
                                msg = fullMsg.substring(0, idx);
                            } else {
                                int idx2 = fullMsg.indexOf("====");
                                if (idx2 != -1 && idx2 > fullMsg.length() - 20) {
                                    msg = fullMsg.substring(0, idx2);
                                }
                            }
                            currentEntry.setLogger(logger);
                            messageBuilder = new StringBuilder(msg);
                        } else {
                            if (currentEntry != null) {
                                messageBuilder.append("\n").append(line);
                            } else {
                                currentEntry = new LogEntryDTO("", "UNKNOWN", "", "", line);
                                logs.add(currentEntry);
                                currentEntry = null;
                            }
                        }
                    }
                    
                    if (currentEntry != null) {
                        currentEntry.setMessage(messageBuilder.toString().trim());
                        logs.add(currentEntry);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read log file", e);
        }
        
        return logs;
    }

    private String extractDateFromFilename(String filename) {
        Pattern p = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
        Matcher m = p.matcher(filename);
        if (m.find()) {
            return m.group(1);
        }
        return filename;
    }
}

