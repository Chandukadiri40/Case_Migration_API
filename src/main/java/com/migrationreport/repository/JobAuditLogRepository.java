package com.migrationreport.repository;

import com.migrationreport.entity.JobAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobAuditLogRepository extends JpaRepository<JobAuditLog, Long> {
    List<JobAuditLog> findByJobIdOrderByModifiedAtDesc(Long jobId);
}
