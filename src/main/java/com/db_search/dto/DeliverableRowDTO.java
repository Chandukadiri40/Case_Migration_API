package com.db_search.dto;

import lombok.Data;

@Data
public class DeliverableRowDTO {
    private String objectStore;
    private String documentClass;
    private Long totalDocuments;
    private Double totalFileSizeGb;
    private Long extractedFileNet;
    private Long extractionFailed;
    private Long remaining;
    private Double extractedFileSizeGb;
    private Double percentCompletion;
    private Double percentFailed;
}