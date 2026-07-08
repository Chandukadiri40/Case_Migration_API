package com.db_search.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class SearchRequest {
    private String table; // "source", "staging", or "target"
    private String status; // "Success", "failed", or "total"
    private String fromDate;
    private String toDate;
    private List<String> docIds; // List of document/object IDs for bulk search
    private Map<String, String> systemFilters; // Key: System column keys -> Value: search value
    private Map<String, String> customFilters; // Key: Custom DB column name -> Value: search value
}
