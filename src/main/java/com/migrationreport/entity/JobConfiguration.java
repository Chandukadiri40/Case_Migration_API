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
public class JobConfiguration {
    private String importTarget; // 'case' or 'is'
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String docIds;
    private Integer workerThreads;
    private Integer batchSize;
    private Integer retryCount;
    private Integer retryInterval;
    private Boolean preserveMetadata;
    private Boolean preserveCreatedDate;
    private Boolean preserveModifiedDate;
    private Boolean validateChecksum;
    private Boolean continueOnError;
    private Boolean generateAudit;
}
