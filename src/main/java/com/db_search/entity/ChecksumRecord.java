package com.db_search.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "ISCHECKSUMTABLE")
public class ChecksumRecord {

    @Id
    @Column(name = "DOCUMENTID", length = 255)
    private String documentId;

    @Column(name = "CHECKSUMBEFORE", length = 255)
    private String checksumBefore;

    @Column(name = "CHECKSUMAFTER", length = 255)
    private String checksumAfter;

    @Column(name = "FILENAME", length = 500)
    private String fileName;

    @Column(name = "CHECKSUM_STATUS", length = 100)
    private String checksumStatus;
}
