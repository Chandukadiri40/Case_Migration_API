package com.migrationreport.controller;

import com.migrationreport.entity.MigrationJob;
import com.migrationreport.repository.MigrationJobRepository;
import com.migrationreport.service.JobExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final MigrationJobRepository jobRepository;
    private final JobExecutionService jobExecutionService;

    @GetMapping
    public ResponseEntity<List<MigrationJob>> getAllJobs(@RequestParam(required = false) String category) {
        log.info("[JOB API] Fetching jobs. Category filter: {}", category);
        List<MigrationJob> jobs;
        if (category != null && !category.trim().isEmpty()) {
            jobs = jobRepository.findByCategoryOrderByCreatedDateDesc(category);
        } else {
            jobs = jobRepository.findAll();
        }
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MigrationJob> getJobById(@PathVariable Long id) {
        return jobRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createJob(@RequestBody MigrationJob job) {
        log.info("[JOB API] Creating new job: {}", job.getName());

        if (job.getName() == null || job.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Job name cannot be empty.");
        }

        String trimmedName = job.getName().trim();
        if (jobRepository.existsByNameIgnoreCase(trimmedName)) {
            log.warn("[JOB API] Job creation rejected: Job name '{}' already exists.", trimmedName);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("A job with the name '" + trimmedName + "' already exists. Job names must be unique.");
        }

        job.setName(trimmedName);
        if (job.getStatus() == null) {
            job.setStatus("Pending");
        }
        if (job.getCreatedDate() == null) {
            job.setCreatedDate(LocalDateTime.now().toString());
        }
        if (job.getRecordsProcessed() == null) {
            job.setRecordsProcessed(0L);
        }

        MigrationJob savedJob = jobRepository.save(job);
        return ResponseEntity.ok(savedJob);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MigrationJob> updateJob(@PathVariable Long id, @RequestBody MigrationJob updatedJob) {
        return jobRepository.findById(id).map(existing -> {
            existing.setName(updatedJob.getName());
            existing.setCategory(updatedJob.getCategory());
            existing.setType(updatedJob.getType());
            existing.setSource(updatedJob.getSource());
            existing.setDateRange(updatedJob.getDateRange());
            existing.setFilterCriteria(updatedJob.getFilterCriteria());
            existing.setRecords(updatedJob.getRecords());
            existing.setEnv(updatedJob.getEnv());
            existing.setCommand(updatedJob.getCommand());
            existing.setLogPath(updatedJob.getLogPath());
            existing.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(jobRepository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
        log.info("[JOB API] Deleting job ID {}", id);
        if (jobRepository.existsById(id)) {
            jobRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<MigrationJob> startJob(@PathVariable Long id) {
        log.info("[JOB API] Triggering start for job ID {}", id);
        MigrationJob job = jobExecutionService.startJob(id);
        return ResponseEntity.ok(job);
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<MigrationJob> stopJob(@PathVariable Long id) {
        log.info("[JOB API] Triggering stop for job ID {}", id);
        MigrationJob job = jobExecutionService.stopJob(id);
        return ResponseEntity.ok(job);
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<MigrationJob> pauseJob(@PathVariable Long id) {
        log.info("[JOB API] Triggering pause for job ID {}", id);
        MigrationJob job = jobExecutionService.pauseJob(id);
        return ResponseEntity.ok(job);
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<List<String>> getJobLogs(@PathVariable Long id) {
        List<String> logs = jobExecutionService.getJobLogs(id);
        return ResponseEntity.ok(logs);
    }
}
