package com.migrationreport.dialect;

public class PostgresDialect implements SqlDialect {

    @Override
    public String getLimitSql(int limit) {
        return " LIMIT " + limit;
    }

    @Override
    public String castToTimestamp(String column) {
        return "CAST(" + column + " AS TIMESTAMP)";
    }

    @Override
    public String castParameterToTimestamp() {
        return "CAST(? AS TIMESTAMP)";
    }

    @Override
    public String extractYear(String column) {
        return "EXTRACT(YEAR FROM " + castToTimestamp(column) + ")";
    }

    @Override
    public String extractMonth(String column) {
        return "EXTRACT(MONTH FROM " + castToTimestamp(column) + ")";
    }

    @Override
    public String calculateEpochDifferenceDays(String startColumn, String endColumn) {
        return "COALESCE(EXTRACT(EPOCH FROM (MAX(" + castToTimestamp(endColumn) + ") - MIN(" + castToTimestamp(startColumn) + "))) / 72000.0, 0)";
    }

    @Override
    public String castToNumeric(String column) {
        return "CAST(" + column + " AS numeric)";
    }

    @Override
    public String castToDate(String column) {
        return "CAST(" + column + " AS DATE)";
    }
}
