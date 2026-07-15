package com.migrationreport.dto;

import lombok.Data;

@Data
public class DeliverableRequest {
    private String applicationName;
    private String documentClass;
    private String createdDate;
    private String startDate;
    private String endDate;
    private String migrationStatus;
}
