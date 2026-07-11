package com.db_search.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "PROPERTYDEFINITION")
public class PropertyDefinition {
    @Id
    @Column(name = "COLUMN_ID", length = 255)
    private String columnId;

    @Column(name = "GLOBAL_PROP_ID", length = 255)
    private String globalPropId;

    @Column(name = "DBG_DISPLAY_NAME", length = 500)
    private String dbgDisplayName;

    @Column(name = "datatype", length = 100)
    private String datatype;
}
