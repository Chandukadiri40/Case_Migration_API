package com.migrationreport.exception;

public class LogParsingException extends RuntimeException {
    public LogParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
