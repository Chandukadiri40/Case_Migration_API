package com.migrationreport.dto.config;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantConfig {
    private List<ApplicationConfig> applications;

    public List<ApplicationConfig> getApplications() {
        return applications;
    }

    public void setApplications(List<ApplicationConfig> applications) {
        this.applications = applications;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApplicationConfig {
        private String appId;
        private String appName;
        private String objectStore;
        private String schema;


        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }

        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }

        public String getObjectStore() { return objectStore; }
        public void setObjectStore(String objectStore) { this.objectStore = objectStore; }

        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }

        private Map<String, List<String>> classifiedTables;
        public Map<String, List<String>> getClassifiedTables() { return classifiedTables; }
        public void setClassifiedTables(Map<String, List<String>> classifiedTables) { this.classifiedTables = classifiedTables; }

        private Map<String, String> primaryColumns;
        public Map<String, String> getPrimaryColumns() { return primaryColumns; }
        public void setPrimaryColumns(Map<String, String> primaryColumns) { this.primaryColumns = primaryColumns; }

        private Map<String, String> systemColumns;
        public Map<String, String> getSystemColumns() { return systemColumns; }
        public void setSystemColumns(Map<String, String> systemColumns) { this.systemColumns = systemColumns; }

        private List<PropertyMapping> propertyMappings;
        public List<PropertyMapping> getPropertyMappings() { return propertyMappings; }
        public void setPropertyMappings(List<PropertyMapping> propertyMappings) { this.propertyMappings = propertyMappings; }
    }

    public static class PropertyMapping {
        private String sourceTable;
        private String sourceColumn;
        private String targetTable;
        private String targetColumn;

        public String getSourceTable() { return sourceTable; }
        public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
        public String getSourceColumn() { return sourceColumn; }
        public void setSourceColumn(String sourceColumn) { this.sourceColumn = sourceColumn; }
        public String getTargetTable() { return targetTable; }
        public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
        public String getTargetColumn() { return targetColumn; }
        public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
    }


}
