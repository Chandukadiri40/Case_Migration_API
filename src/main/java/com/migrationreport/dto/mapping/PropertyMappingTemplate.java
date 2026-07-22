package com.migrationreport.dto.mapping;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PropertyMappingTemplate {
    private String templateId;
    private String templateName;
    private String applicationId;
    private String sourceDocumentClass;
    private String targetDocumentClass;
    private List<PropertyMap> mappings;
    private String lastModifiedBy;
    private String lastModifiedDate;

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public String getSourceDocumentClass() { return sourceDocumentClass; }
    public void setSourceDocumentClass(String sourceDocumentClass) { this.sourceDocumentClass = sourceDocumentClass; }

    public String getTargetDocumentClass() { return targetDocumentClass; }
    public void setTargetDocumentClass(String targetDocumentClass) { this.targetDocumentClass = targetDocumentClass; }

    public List<PropertyMap> getMappings() { return mappings; }
    public void setMappings(List<PropertyMap> mappings) { this.mappings = mappings; }

    public String getLastModifiedBy() { return lastModifiedBy; }
    public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }

    public String getLastModifiedDate() { return lastModifiedDate; }
    public void setLastModifiedDate(String lastModifiedDate) { this.lastModifiedDate = lastModifiedDate; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PropertyMap {
        private String sourceProperty;
        private String sourceSymbolicName;
        private String sourceDataType;
        private String targetProperty;
        private String targetSymbolicName;
        private String targetDataType;

        public String getSourceProperty() { return sourceProperty; }
        public void setSourceProperty(String sourceProperty) { this.sourceProperty = sourceProperty; }

        public String getSourceSymbolicName() { return sourceSymbolicName; }
        public void setSourceSymbolicName(String sourceSymbolicName) { this.sourceSymbolicName = sourceSymbolicName; }
        
        public String getSourceDataType() { return sourceDataType; }
        public void setSourceDataType(String sourceDataType) { this.sourceDataType = sourceDataType; }
        
        public String getTargetProperty() { return targetProperty; }
        public void setTargetProperty(String targetProperty) { this.targetProperty = targetProperty; }

        public String getTargetSymbolicName() { return targetSymbolicName; }
        public void setTargetSymbolicName(String targetSymbolicName) { this.targetSymbolicName = targetSymbolicName; }
        
        public String getTargetDataType() { return targetDataType; }
        public void setTargetDataType(String targetDataType) { this.targetDataType = targetDataType; }
    }
}
