package com.migrationreport.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "MIGRATION_JOBS")
public class MigrationJob {

    @Transient
    private String modificationReason;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, unique = true)
    private String name;

    @Column(name = "category", nullable = false)
    private String category; // extraction, import_doc, import_metadata

    @Column(name = "job_type")
    private String type; // Bulk, Ad-hoc, Exception

    @Column(name = "source_system")
    private String source; // IBM IS, FileNet P8, PostgreSQL, Local File System

    @Column(name = "date_range")
    private String dateRange;

    @Column(name = "filter_criteria")
    private String filterCriteria;

    @Column(name = "expected_records")
    private Long records;

    @Column(name = "processed_records")
    private Long recordsProcessed;

    @Column(name = "status")
    private String status; // Pending, Running, Completed, Failed, Paused

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_date")
    private String createdDate;

    @Column(name = "environment")
    private String env; // Ubuntu Server 24.04 LTS (192.168.1.105)

    @Column(name = "command_text", length = 1000)
    private String command;

    @Column(name = "log_path")
    private String logPath;

    // Detailed Config Fields for Edit Modals
    @Column(name = "import_target")
    private String importTarget; // 'case' or 'is'

    @Column(name = "start_date_param")
    private String startDate;

    @Column(name = "end_date_param")
    private String endDate;

    @Column(name = "doc_ids_param", length = 1000)
    private String docIds;

    @Column(name = "worker_threads")
    private Integer workerThreads;

    @Column(name = "batch_size")
    private Integer batchSize;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "retry_interval")
    private Integer retryInterval;

    @Column(name = "preserve_metadata")
    private Boolean preserveMetadata;

    @Column(name = "preserve_created_date")
    private Boolean preserveCreatedDate;

    @Column(name = "preserve_modified_date")
    private Boolean preserveModifiedDate;

    @Column(name = "validate_checksum")
    private Boolean validateChecksum;

    @Column(name = "continue_on_error")
    private Boolean continueOnError;

    @Column(name = "generate_audit")
    private Boolean generateAudit;

    @Column(name = "process_pid")
    private Long processPid;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration")
    private String duration; // e.g. "45s", "2m 14s", "1h 05m"

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
