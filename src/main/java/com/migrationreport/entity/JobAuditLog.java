package com.migrationreport.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_audit_log")
@Data
@NoArgsConstructor
public class JobAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "modification_reason", columnDefinition = "TEXT")
    private String modificationReason;

    @Column(name = "modified_by")
    private String modifiedBy;

    @Column(name = "changes_made", columnDefinition = "TEXT")
    private String changesMade;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    @PrePersist
    protected void onCreate() {
        modifiedAt = LocalDateTime.now();
    }
}
