package com.migrationreport.controller;

import com.migrationreport.entity.MigrationJob;
import com.migrationreport.repository.JobAuditLogRepository;
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
    private final JobAuditLogRepository jobAuditLogRepository;

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
    public ResponseEntity<?> updateJob(@PathVariable Long id, @RequestBody MigrationJob updatedJob) {
        return jobRepository.findById(id).map(existing -> {
            if ("Completed".equalsIgnoreCase(existing.getStatus())) {
                if (updatedJob.getModificationReason() == null || updatedJob.getModificationReason().trim().isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(java.util.Map.of("error", "A modification reason is required when editing a Completed job."));
                }
                boolean isChanged = !java.util.Objects.equals(existing.getFilterCriteria(), updatedJob.getFilterCriteria())
                        || !java.util.Objects.equals(existing.getImportTarget(), updatedJob.getImportTarget())
                        || !java.util.Objects.equals(existing.getCommand(), updatedJob.getCommand())
                        || !java.util.Objects.equals(existing.getDateRange(), updatedJob.getDateRange())
                        || !java.util.Objects.equals(existing.getDocIds(), updatedJob.getDocIds())
                        || !java.util.Objects.equals(existing.getSource(), updatedJob.getSource());
                
                if (!isChanged) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(java.util.Map.of("error", "At least one configuration property must be changed to modify a Completed job."));
                }
                
                com.migrationreport.entity.JobAuditLog audit = new com.migrationreport.entity.JobAuditLog();
                audit.setJobId(id);
                audit.setModificationReason(updatedJob.getModificationReason());
                audit.setModifiedBy("admin"); 
                audit.setChangesMade("Changed configuration from previous state. Target: " + updatedJob.getImportTarget() + ", Criteria: " + updatedJob.getFilterCriteria());
                jobAuditLogRepository.save(audit);
                
                existing.setStatus("Pending");
                existing.setRecordsProcessed(0L);
                existing.setDuration(null);
            }

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
            
            // New Config Fields mapping
            existing.setImportTarget(updatedJob.getImportTarget());
            existing.setStartDate(updatedJob.getStartDate());
            existing.setEndDate(updatedJob.getEndDate());
            existing.setDocIds(updatedJob.getDocIds());
            existing.setWorkerThreads(updatedJob.getWorkerThreads());
            existing.setBatchSize(updatedJob.getBatchSize());
            existing.setRetryCount(updatedJob.getRetryCount());
            existing.setRetryInterval(updatedJob.getRetryInterval());
            existing.setPreserveMetadata(updatedJob.getPreserveMetadata());
            existing.setPreserveCreatedDate(updatedJob.getPreserveCreatedDate());
            existing.setPreserveModifiedDate(updatedJob.getPreserveModifiedDate());
            existing.setValidateChecksum(updatedJob.getValidateChecksum());
            existing.setContinueOnError(updatedJob.getContinueOnError());
            existing.setGenerateAudit(updatedJob.getGenerateAudit());
            
            existing.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(jobRepository.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<MigrationJob> updateJobStatus(@PathVariable Long id, @RequestBody java.util.Map<String, Object> updates) {
        return jobRepository.findById(id).map(existing -> {
            if (updates.containsKey("status")) existing.setStatus((String) updates.get("status"));
            if (updates.containsKey("processPid")) {
                Object pid = updates.get("processPid");
                existing.setProcessPid(pid == null ? null : Long.valueOf(pid.toString()));
            }
            if (updates.containsKey("duration")) existing.setDuration((String) updates.get("duration"));
            if (updates.containsKey("startTime")) {
                existing.setStartTime(LocalDateTime.now()); // For simplicity
            }
            if (updates.containsKey("recordsProcessed")) {
                Object rp = updates.get("recordsProcessed");
                existing.setRecordsProcessed(rp == null ? 0L : Long.valueOf(rp.toString()));
            }
            if (updates.containsKey("records")) {
                Object r = updates.get("records");
                existing.setRecords(r == null ? 0L : Long.valueOf(r.toString()));
            }
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
