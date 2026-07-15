package com.migrationreport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;

import java.sql.Timestamp;

@Data
@MappedSuperclass
public abstract class BaseDocVersion {

    @Id
    @Column(name = "OBJECT_ID", length = 255)
    private String objectId;

    @Column(name = "CREATE_DATE")
    private Timestamp createDate;

    @Column(name = "CONTENT_SIZE")
    private Long contentSize;

    @Column(name = "MIME_TYPE", length = 255)
    private String mimeType;

    @Column(name = "MIGRATED_DATE")
    private Timestamp migratedDate;

    @Column(name = "MIGRATION_STATUS", length = 100)
    private String migrationStatus;

    @Column(name = "OBJECT_CLASS_ID", length = 255)
    private String objectClassId;

    @Column(name = "U1708_DOCUMENTTITLE", length = 1000)
    private String documentTitle;
}
