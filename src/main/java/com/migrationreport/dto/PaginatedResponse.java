package com.migrationreport.dto;

public class PaginatedResponse<T> {
    private long totalRecords;
    private T data;

    public PaginatedResponse() {
    }

    public PaginatedResponse(long totalRecords, T data) {
        this.totalRecords = totalRecords;
        this.data = data;
    }

    public long getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(long totalRecords) {
        this.totalRecords = totalRecords;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
