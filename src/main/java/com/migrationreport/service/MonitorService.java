package com.migrationreport.service;

import com.migrationreport.dto.LogConfigDTO;
import com.migrationreport.dto.LogEntryDTO;
import com.migrationreport.security.PathValidator;
import com.migrationreport.exception.LogParsingException;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicReference;

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
import java.io.IOException;

@Service
public class MonitorService {

    private static final String USER_DIR = "user.dir";
    private final AtomicReference<LogConfigDTO> currentConfig = new AtomicReference<>();

    private static final Pattern LOG_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?) +([A-Z]+) +.*? --- \\[(.*?)\\] +(.*?) : (.*)$");
    private static final Pattern CUSTOM_LOG_PATTERN = Pattern.compile("^(\\d{2}-\\d{2}-\\d{4} \\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})? [AP]M) - (.*)$");

    public LogConfigDTO saveConfig(LogConfigDTO config) {
        Path path = PathValidator.validateAndNormalize(config.getLogPath(), System.getProperty(USER_DIR));
        LogConfigDTO validatedConfig = new LogConfigDTO(path.toString());
        this.currentConfig.set(validatedConfig);
        return validatedConfig;
    }

    public LogConfigDTO getConfig() {
        return this.currentConfig.get();
    }

    public List<String> getAvailableDates() {
        LogConfigDTO cfg = this.currentConfig.get();
        if (cfg == null) {
            return new ArrayList<>();
        }
        
        Path dirPath = PathValidator.validateAndNormalize(cfg.getLogPath(), System.getProperty(USER_DIR));
        
        try (Stream<Path> paths = Files.list(dirPath)) {
            return paths
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(name -> name.toLowerCase().matches(".*\\d{4}-\\d{2}-\\d{2}\\.log$") || name.toLowerCase().endsWith(".log"))
                .map(this::extractDateFromFilename)
                .distinct()
                .sorted((a, b) -> b.compareTo(a))
                .toList();
        } catch (Exception e) {
            throw new LogParsingException("Failed to scan log directory", e);
        }
    }

    public List<LogEntryDTO> getLogsByDate(String date) {
        LogConfigDTO cfg = this.currentConfig.get();
        if (cfg == null) {
            return new ArrayList<>();
        }
        
        Path dirPath = PathValidator.validateAndNormalize(cfg.getLogPath(), System.getProperty(USER_DIR));
        
        if (date == null || date.contains("/") || date.contains("\\") || date.contains("..")) {
            throw new IllegalArgumentException("Invalid date format");
        }
        
        try (Stream<Path> paths = Files.list(dirPath)) {
            Path targetFile = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().contains(date))
                .findFirst()
                .orElse(null);
                
            if (targetFile != null) {
                return parseLogFile(targetFile);
            }
        } catch (Exception e) {
            throw new LogParsingException("Failed to read logs", e);
        }
        
        return new ArrayList<>();
    }

    private List<LogEntryDTO> parseLogFile(Path targetFile) throws IOException {
        List<LogEntryDTO> logs = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(targetFile.toFile()))) {
            String line;
            LogEntryDTO currentEntry = null;
            StringBuilder messageBuilder = new StringBuilder();
            
            while ((line = reader.readLine()) != null) {
                currentEntry = processLogLine(line, logs, currentEntry, messageBuilder);
            }
            if (currentEntry != null) {
                currentEntry.setMessage(messageBuilder.toString().trim());
                logs.add(currentEntry);
            }
        }
        return logs;
    }

    private LogEntryDTO processLogLine(String line, List<LogEntryDTO> logs, LogEntryDTO currentEntry, StringBuilder messageBuilder) {
        Matcher matcher = LOG_PATTERN.matcher(line);
        Matcher customMatcher = CUSTOM_LOG_PATTERN.matcher(line);
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
            messageBuilder.setLength(0);
            messageBuilder.append(matcher.group(5));
        } else if (customMatcher.matches()) {
            if (currentEntry != null) {
                currentEntry.setMessage(messageBuilder.toString().trim());
                logs.add(currentEntry);
            }
            currentEntry = new LogEntryDTO();
            currentEntry.setTimestamp(customMatcher.group(1));
            currentEntry.setLevel("INFO");
            currentEntry.setThread("");
            currentEntry.setLogger("");
            messageBuilder.setLength(0);
            messageBuilder.append(customMatcher.group(2));
        } else {
            if (currentEntry != null) {
                messageBuilder.append("\n").append(line);
            } else {
                currentEntry = new LogEntryDTO("", "UNKNOWN", "", "", line);
                logs.add(currentEntry);
                currentEntry = null;
            }
        }
        return currentEntry;
    }

    private String extractDateFromFilename(String filename) {
        Pattern p = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
        Matcher m = p.matcher(filename);
        if (m.find()) {
            return m.group();
        }
        return filename;
    }
}

