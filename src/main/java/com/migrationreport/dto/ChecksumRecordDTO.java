package com.migrationreport.dto;

import lombok.Data;

@Data
public class ChecksumRecordDTO {
    private String documentId;
    private String fileName;
    private String checksumBefore;
    private String checksumAfter;
    private String checksumStatus;
    private String migrationStatus;
    private String migratedDate;
    private String documentTitle;
    private String documentClass;
}
