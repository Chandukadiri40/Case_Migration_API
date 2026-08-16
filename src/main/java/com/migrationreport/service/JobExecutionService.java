package com.migrationreport.service;

import com.migrationreport.entity.MigrationJob;
import com.migrationreport.repository.MigrationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobExecutionService {

    private final MigrationJobRepository jobRepository;
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();
    private final Map<Long, Process> activeProcesses = new ConcurrentHashMap<>();

    public MigrationJob startJob(Long jobId) {
        MigrationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found with ID: " + jobId));

        if ("Running".equalsIgnoreCase(job.getStatus()) && isProcessRunning(job)) {
            log.info("[JOB EXECUTOR] Job ID {} is already running under PID {}", jobId, job.getProcessPid());
            return job;
        }

        try {
            String command = job.getCommand();
            if (command == null || command.trim().isEmpty()) {
                throw new IllegalArgumentException("Job command string cannot be empty.");
            }

            // Create log directory if missing
            String logPathStr = job.getLogPath();
            if (logPathStr != null && !logPathStr.trim().isEmpty()) {
                Path parentDir = Paths.get(logPathStr).getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }
            }

            ProcessBuilder processBuilder = createProcessBuilder(command, job.getLogPath());
            LocalDateTime startTime = LocalDateTime.now();
            Process process = processBuilder.start();

            long pid = process.pid();
            activeProcesses.put(jobId, process);

            job.setStatus("Running");
            job.setProcessPid(pid);
            job.setStartTime(startTime);
            job.setEndTime(null);
            job.setDuration("Running...");
            job.setUpdatedAt(startTime);
            jobRepository.save(job);

            log.info("[JOB EXECUTOR] Successfully started job ID {} ({}) with PID {} at {}", jobId, job.getName(), pid, startTime);

            // Asynchronously monitor process termination
            asyncExecutor.submit(() -> {
                try {
                    int exitCode = process.waitFor();
                    activeProcesses.remove(jobId);

                    LocalDateTime endTime = LocalDateTime.now();
                    MigrationJob latestJob = jobRepository.findById(jobId).orElse(job);
                    
                    Duration elapsed = Duration.between(startTime, endTime);
                    String durationStr = formatDuration(elapsed);

                    latestJob.setEndTime(endTime);
                    latestJob.setDuration(durationStr);
                    latestJob.setProcessPid(null);

                    if (exitCode == 0) {
                        latestJob.setStatus("Completed");
                        latestJob.setRecordsProcessed(latestJob.getRecords());
                        log.info("[JOB EXECUTOR] Job ID {} ({}) completed in {} with exit code 0", jobId, latestJob.getName(), durationStr);
                    } else {
                        if (!"Paused".equalsIgnoreCase(latestJob.getStatus())) {
                            latestJob.setStatus("Failed");
                        }
                        log.warn("[JOB EXECUTOR] Job ID {} ({}) failed after {} with exit code {}", jobId, latestJob.getName(), durationStr, exitCode);
                    }
                    latestJob.setUpdatedAt(endTime);
                    jobRepository.save(latestJob);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("[JOB EXECUTOR] Interrupted while waiting for job ID {}", jobId, e);
                }
            });

            return job;

        } catch (Exception e) {
            log.error("[JOB EXECUTOR] Failed to launch process for job ID {}", jobId, e);
            job.setStatus("Failed");
            job.setProcessPid(null);
            job.setUpdatedAt(LocalDateTime.now());
            return jobRepository.save(job);
        }
    }

    public MigrationJob stopJob(Long jobId) {
        MigrationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found with ID: " + jobId));

        Process process = activeProcesses.remove(jobId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        } else if (job.getProcessPid() != null) {
            ProcessHandle.of(job.getProcessPid()).ifPresent(ProcessHandle::destroyForcibly);
        }

        LocalDateTime endTime = LocalDateTime.now();
        job.setStatus("Failed");
        job.setProcessPid(null);
        job.setEndTime(endTime);
        if (job.getStartTime() != null) {
            job.setDuration(formatDuration(Duration.between(job.getStartTime(), endTime)) + " (Stopped)");
        }
        job.setUpdatedAt(endTime);
        log.info("[JOB EXECUTOR] Job ID {} ({}) stopped by user request", jobId, job.getName());
        return jobRepository.save(job);
    }

    public MigrationJob pauseJob(Long jobId) {
        MigrationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found with ID: " + jobId));

        Process process = activeProcesses.remove(jobId);
        if (process != null && process.isAlive()) {
            process.destroy();
        } else if (job.getProcessPid() != null) {
            ProcessHandle.of(job.getProcessPid()).ifPresent(ProcessHandle::destroy);
        }

        LocalDateTime endTime = LocalDateTime.now();
        job.setStatus("Paused");
        job.setProcessPid(null);
        job.setEndTime(endTime);
        if (job.getStartTime() != null) {
            job.setDuration(formatDuration(Duration.between(job.getStartTime(), endTime)) + " (Paused)");
        }
        job.setUpdatedAt(endTime);
        log.info("[JOB EXECUTOR] Job ID {} ({}) paused by user request", jobId, job.getName());
        return jobRepository.save(job);
    }

    public List<String> getJobLogs(Long jobId) {
        MigrationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found with ID: " + jobId));

        String logPathStr = job.getLogPath();
        if (logPathStr == null || logPathStr.trim().isEmpty()) {
            return Collections.singletonList("[INFO] No log file path configured for job: " + job.getName());
        }

        Path path = Paths.get(logPathStr);
        if (!Files.exists(path)) {
            return Collections.singletonList("[INFO] Log file not created yet at: " + logPathStr + ". Job Status: " + job.getStatus());
        }

        try {
            List<String> allLines = Files.readAllLines(path, StandardCharsets.UTF_8);
            int maxLines = 150;
            if (allLines.size() > maxLines) {
                return allLines.subList(allLines.size() - maxLines, allLines.size());
            }
            return allLines;
        } catch (Exception e) {
            log.error("Failed to read log file for job ID {}", jobId, e);
            return Collections.singletonList("[ERROR] Failed to read log file: " + e.getMessage());
        }
    }

    private ProcessBuilder createProcessBuilder(String command, String logPathStr) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessBuilder pb;

        if (isWindows) {
            pb = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            pb = new ProcessBuilder("bash", "-c", command);
        }

        if (logPathStr != null && !logPathStr.trim().isEmpty()) {
            File logFile = new File(logPathStr);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));
        } else {
            pb.redirectErrorStream(true);
        }

        return pb;
    }

    private boolean isProcessRunning(MigrationJob job) {
        if (job.getProcessPid() == null) return false;
        return ProcessHandle.of(job.getProcessPid()).map(ProcessHandle::isAlive).orElse(false);
    }

    private String formatDuration(Duration d) {
        long seconds = d.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remSeconds = seconds % 60;
        if (minutes < 60) {
            return String.format("%dm %02ds", minutes, remSeconds);
        }
        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        return String.format("%dh %02dm %02ds", hours, remMinutes, remSeconds);
    }
}
