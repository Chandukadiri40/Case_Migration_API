package com.db_search.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogEntryDTO {
    private String timestamp;
    private String level;
    private String logger;
    private String thread;
    private String message;
}

