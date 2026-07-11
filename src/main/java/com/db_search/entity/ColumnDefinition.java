package com.db_search.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "COLUMNDEFINITION")
public class ColumnDefinition {
    @Id
    @Column(name = "OBJECT_ID", length = 255)
    private String objectId;

    @Column(name = "DBG_TABLE_NAME", length = 255)
    private String dbgTableName;

    @Column(name = "COLUMN_NAME", length = 255)
    private String columnName;
}
