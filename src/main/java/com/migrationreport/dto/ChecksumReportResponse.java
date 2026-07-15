package com.migrationreport.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ChecksumReportResponse {
    private Map<String, Long> summary;
    private List<Map<String, Object>> records;
}
