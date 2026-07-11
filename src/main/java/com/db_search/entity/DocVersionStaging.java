package com.db_search.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "docversion_staging")
public class DocVersionStaging extends BaseDocVersion {
    // Inherits base columns including U1708_DOCUMENTTITLE
}
