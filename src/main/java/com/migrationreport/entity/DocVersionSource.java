package com.migrationreport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "docversion_source")
public class DocVersionSource extends BaseDocVersion {

    @Column(name = "UA8C8_USER_NAME", length = 255)
    private String userName;

    @Column(name = "UD5E8_ADDRESS", length = 1000)
    private String address;

    @Column(name = "UC7A6_ORDER_NO", length = 255)
    private String orderNo;
}
