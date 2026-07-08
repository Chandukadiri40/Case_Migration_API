package com.db_search.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetadataFieldDTO {
    private String columnName;
    private String symbolicName;
    private String displayName;
    private Integer dataType;
}
