package com.migrationreport.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobAuditEntry {
    private String changesMade;
    private String modificationReason;
    private LocalDateTime modifiedAt;
    private String modifiedBy;
}
