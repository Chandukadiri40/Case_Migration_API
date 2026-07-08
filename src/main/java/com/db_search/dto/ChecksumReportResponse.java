package com.db_search.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ChecksumReportResponse {
    private Map<String, Long> summary;
    private List<ChecksumRecordDTO> records;
}
