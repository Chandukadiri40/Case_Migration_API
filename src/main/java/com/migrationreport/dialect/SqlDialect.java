package com.migrationreport.dialect;

public interface SqlDialect {
    /**
     * Appends a LIMIT clause to the end of a SQL string.
     */
    String getLimitSql(int limit);

    /**
     * Casts a column to a timestamp/datetime.
     */
    String castToTimestamp(String column);

    /**
     * Casts a parameter (?) to a timestamp/datetime.
     */
    String castParameterToTimestamp();

    /**
     * Extracts the year from a timestamp column.
     */
    String extractYear(String column);

    /**
     * Extracts the month from a timestamp column.
     */
    String extractMonth(String column);

    /**
     * Calculates the epoch difference (in days) between two timestamps.
     */
    String calculateEpochDifferenceDays(String startColumn, String endColumn);
    
    /**
     * Safely casts a column to numeric for aggregation.
     */
    String castToNumeric(String column);
    
    /**
     * Safely casts a column to date (without time).
     */
    String castToDate(String column);
    
    /**
     * Returns the string length function (LENGTH for PostgreSQL, LEN for SQL Server).
     */
    String getLength(String column);
}
