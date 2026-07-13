package com.db_search.dto;

import lombok.Data;

@Data
public class ChecksumReportRequest {
    private String appId;
    private String documentClass;
    private String fromDate;
    private String toDate;
    private String migrationStatus;
}
