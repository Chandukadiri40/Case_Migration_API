package com.migrationreport.dto;

import java.time.LocalDate;
import java.util.List;

public class ExceptionCriteria {
    private String appId;
    private List<String> documentClasses;
    private String objectId;
    private LocalDate createdFrom;
    private LocalDate createdTo;
    private List<CustomMetadataFilter> customMetadata;
    private int page = 1;
    private int pageSize = 50;

    public ExceptionCriteria() {
        // default constructor
    }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public List<String> getDocumentClasses() { return documentClasses; }
    public void setDocumentClasses(List<String> documentClasses) { this.documentClasses = documentClasses; }

    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }

    public LocalDate getCreatedFrom() { return createdFrom; }
    public void setCreatedFrom(LocalDate createdFrom) { this.createdFrom = createdFrom; }

    public LocalDate getCreatedTo() { return createdTo; }
    public void setCreatedTo(LocalDate createdTo) { this.createdTo = createdTo; }

    public List<CustomMetadataFilter> getCustomMetadata() { return customMetadata; }
    public void setCustomMetadata(List<CustomMetadataFilter> customMetadata) { this.customMetadata = customMetadata; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}
