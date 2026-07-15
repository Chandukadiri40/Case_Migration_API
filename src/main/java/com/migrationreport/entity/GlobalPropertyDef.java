package com.migrationreport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "GLOBALPROPERTYDEF")
public class GlobalPropertyDef {
    @Id
    @Column(name = "OBJECT_ID", length = 255)
    private String objectId;

    @Column(name = "SYMBOLIC_NAME", length = 500)
    private String symbolicName;
}
