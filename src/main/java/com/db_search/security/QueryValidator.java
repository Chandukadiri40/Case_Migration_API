package com.db_search.security;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
public class QueryValidator {

    // Regex pattern to check for unauthorized SQL commands or nested statements
    private static final Pattern BLACKLIST_PATTERN = Pattern.compile(
        "\\b(insert|update|delete|drop|alter|create|truncate|rename|replace|grant|revoke|merge|exec|execute|union|select)\\b",
        Pattern.CASE_INSENSITIVE
    );

    public void validateQueryFragment(String queryFragment) {
        if (queryFragment == null || queryFragment.trim().isEmpty()) {
            throw new IllegalArgumentException("Query fragment cannot be empty");
        }

        // 1. Block statement chaining (semicolons) to prevent multi-statement injection
        if (queryFragment.contains(";")) {
            throw new IllegalArgumentException("Query chaining is prohibited. Semicolons ';' are not allowed.");
        }

        // 2. Block SQL comments that could be used to truncate queries (e.g. -- or /* */)
        if (queryFragment.contains("--") || queryFragment.contains("/*") || queryFragment.contains("*/")) {
            throw new IllegalArgumentException("SQL comments are not allowed in search parameters.");
        }

        // 3. Block blacklisted SQL keywords (e.g. UNION, SELECT nested, INSERT, DROP, etc.)
        if (BLACKLIST_PATTERN.matcher(queryFragment).find()) {
            throw new IllegalArgumentException("Query contains unauthorized keywords or clauses. Only conditional filters (e.g. WHERE clause components) are allowed.");
        }
    }
}
