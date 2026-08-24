package com.migrationreport.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "environment")
    private String env; // Ubuntu Server 24.04 LTS (192.168.1.105)

    @Column(name = "command_text", length = 1000)
    private String command;

    @Column(name = "log_path")
    private String logPath;

    // Detailed Config Fields stored as JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_configuration", columnDefinition = "jsonb")
    private JobConfiguration configuration;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_history", columnDefinition = "jsonb")
    @Builder.Default
    private List<JobAuditEntry> auditHistory = new java.util.ArrayList<>();

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
