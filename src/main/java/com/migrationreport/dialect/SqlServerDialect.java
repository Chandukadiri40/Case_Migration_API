package com.migrationreport.dialect;

public class SqlServerDialect implements SqlDialect {

    private static final String CAST_PREFIX = "CAST(";

    @Override
    public String getLimitSql(int limit) {
        return " ORDER BY (SELECT NULL) OFFSET 0 ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }

    @Override
    public String castToTimestamp(String column) {
        return CAST_PREFIX + column + " AS DATETIME2)";
    }

    @Override
    public String castParameterToTimestamp() {
        return CAST_PREFIX + "? AS DATETIME2)";
    }

    @Override
    public String extractYear(String column) {
        return "DATEPART(YEAR, " + castToTimestamp(column) + ")";
    }

    @Override
    public String extractMonth(String column) {
        return "DATEPART(MONTH, " + castToTimestamp(column) + ")";
    }

    @Override
    public String calculateEpochDifferenceDays(String startColumn, String endColumn) {
        // SQL Server DATEDIFF in seconds, converted to rough days using the same math (72000.0) from original
        return "COALESCE(CAST(DATEDIFF(SECOND, MIN(" + castToTimestamp(startColumn) + "), MAX(" + castToTimestamp(endColumn) + ")) AS NUMERIC) / 72000.0, 0)";
    }

    @Override
    public String castToNumeric(String column) {
        return CAST_PREFIX + column + " AS NUMERIC(18,2))";
    }

    @Override
    public String castToDate(String column) {
        return CAST_PREFIX + column + " AS DATE)";
    }

    @Override
    public String getLength(String column) {
        return "LEN(" + column + ")";
    }

    @Override
    public String getPaginationSql(int limit, int offset) {
        // If there's no ORDER BY, OFFSET requires it in SQL Server. But some queries might already have an ORDER BY.
        // For simplicity and since we append to the end of where clauses, we force an order if not present?
        // Wait, Deliverables detail query has no ORDER BY. We will append " ORDER BY (SELECT NULL) OFFSET {offset} ROWS FETCH NEXT {limit} ROWS ONLY"
        // just like the getLimitSql implementation.
        return " ORDER BY (SELECT NULL) OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }
}
