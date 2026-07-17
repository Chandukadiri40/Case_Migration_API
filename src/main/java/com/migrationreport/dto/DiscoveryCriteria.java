package com.migrationreport.dto;

import java.time.LocalDate;
import java.util.List;

public class DiscoveryCriteria {
    private String appId;
    private List<String> documentClasses;
    private LocalDate createdFrom;
    private LocalDate createdTo;
    private Integer year;
    private Integer month;
    private List<String> mimeTypes;
    private String sizeBucket;
    private String hasAnnotations;
    private String versionCountBucket;
    private String contentLocatorType;

    public DiscoveryCriteria() {
        // default constructor
    }

    // Getters and Setters
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public List<String> getDocumentClasses() { return documentClasses; }
    public void setDocumentClasses(List<String> documentClasses) { this.documentClasses = documentClasses; }

    public LocalDate getCreatedFrom() { return createdFrom; }
    public void setCreatedFrom(LocalDate createdFrom) { this.createdFrom = createdFrom; }

    public LocalDate getCreatedTo() { return createdTo; }
    public void setCreatedTo(LocalDate createdTo) { this.createdTo = createdTo; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }

    public List<String> getMimeTypes() { return mimeTypes; }
    public void setMimeTypes(List<String> mimeTypes) { this.mimeTypes = mimeTypes; }

    public String getSizeBucket() { return sizeBucket; }
    public void setSizeBucket(String sizeBucket) { this.sizeBucket = sizeBucket; }

    public String getHasAnnotations() { return hasAnnotations; }
    public void setHasAnnotations(String hasAnnotations) { this.hasAnnotations = hasAnnotations; }

    public String getVersionCountBucket() { return versionCountBucket; }
    public void setVersionCountBucket(String versionCountBucket) { this.versionCountBucket = versionCountBucket; }

    public String getContentLocatorType() { return contentLocatorType; }
    public void setContentLocatorType(String contentLocatorType) { this.contentLocatorType = contentLocatorType; }
}
