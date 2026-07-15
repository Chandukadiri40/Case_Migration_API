package com.migrationreport.service;

import com.migrationreport.dto.SearchRequest;
import com.migrationreport.security.QueryValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.migrationreport.exception.ResourceNotFoundException;

@Slf4j
@Service
public class SearchService {

    private final JdbcTemplate jdbcTemplate;
    private final QueryValidator queryValidator;
    private final ConfigurationService configurationService;
    private final String targetTable;

    @org.springframework.beans.factory.annotation.Autowired
    private com.migrationreport.dialect.SqlDialect dialect;

    // Multi-table search configuration
    private final String sourceTable;
    private final String stagingTable;
    private final String targetTableConfig;

    // System columns configuration mappings
    private final String docIdColumn;
    private final String dateColumn;
    private final String createdDateColumn;
    private final String contentSizeColumn;
    private final String mimeTypeColumn;

    // Status column configuration
    private final String statusColumn;

    // Whitelisted custom columns per table configuration
    private final Set<String> sourceCustomColumns;
    private final List<String> sourceCustomColumnsList;

    private final Set<String> stagingCustomColumns;
    private final List<String> stagingCustomColumnsList;

    private final Set<String> targetCustomColumns;
    private final List<String> targetCustomColumnsList;

    private final List<String> reconciliationSystemProperties;
    private final List<String> reconciliationCustomMetadata;

    // Dynamic metadata fields mapping loaded from database (with static fallback)
    private final List<com.migrationreport.dto.MetadataFieldDTO> availableFields = new ArrayList<>();
    private final Map<String, String> displayNameToColumnName = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean fieldsLoaded = false;

    private synchronized void ensureFieldsLoaded() {
        if (fieldsLoaded) {
            return;
        }
        try {
            String sql = "SELECT distinct cd.COLUMN_NAME       AS COLUMN_NAME, " +
                         "       gpd.SYMBOLIC_NAME    AS SYMBOLIC_NAME, " +
                         "       pd.DBG_DISPLAY_NAME  AS DISPLAY_NAME, " +
                         "       pd.datatype          AS DATA_TYPE " +
                         "FROM PROPERTYDEFINITION pd " +
                         "JOIN COLUMNDEFINITION cd ON pd.COLUMN_ID = cd.OBJECT_ID " +
                         "JOIN GLOBALPROPERTYDEF gpd ON pd.GLOBAL_PROP_ID = gpd.OBJECT_ID " +
                         "WHERE cd.DBG_TABLE_NAME = 'DocVersion' " +
                         "ORDER BY cd.COLUMN_NAME";
            
            List<com.migrationreport.dto.MetadataFieldDTO> fields = jdbcTemplate.query(sql, (rs, rowNum) -> {
                String typeStr = rs.getString("DATA_TYPE");
                Integer typeVal = 8; // Default to String (8)
                if (typeStr != null) {
                    try {
                        typeVal = Integer.parseInt(typeStr.trim());
                    } catch (NumberFormatException e) {
                        // ignore and default to 8
                    }
                }
                return new com.migrationreport.dto.MetadataFieldDTO(
                    rs.getString("COLUMN_NAME"),
                    rs.getString("SYMBOLIC_NAME"),
                    rs.getString("DISPLAY_NAME"),
                    typeVal
                );
            });

            if (fields != null && !fields.isEmpty()) {
                availableFields.clear();
                displayNameToColumnName.clear();
                java.util.Set<String> seenNames = new java.util.HashSet<>();
                for (com.migrationreport.dto.MetadataFieldDTO field : fields) {
                    if (field.getDisplayName() == null || field.getDisplayName().trim().isEmpty()) {
                        continue;
                    }
                    String normName = field.getDisplayName().toLowerCase().trim().replaceAll("\\s+", " ");
                    if (seenNames.contains(normName)) {
                        continue;
                    }
                    seenNames.add(normName);
                    
                    availableFields.add(field);
                    if (field.getColumnName() != null) {
                        displayNameToColumnName.put(normName, field.getColumnName().trim());
                    }
                }
                log.info("Loaded {} unique metadata fields mapping from database.", availableFields.size());
            } else {
                loadStaticFallbackFields();
            }
        } catch (Exception e) {
            log.error("Failed to query DB metadata mapping tables: {}. Using fallback static mapping.", e.getMessage(), e);
            loadStaticFallbackFields();
        } finally {
            fieldsLoaded = true;
        }
    }

    private void loadStaticFallbackFields() {
        availableFields.clear();
        displayNameToColumnName.clear();

        List<com.migrationreport.dto.MetadataFieldDTO> fallbackList = List.of(
            new com.migrationreport.dto.MetadataFieldDTO("U1708_DOCUMENTTITLE", "DocumentTitle", "Document Title", 8),
            new com.migrationreport.dto.MetadataFieldDTO("UA8C8_USER_NAME", "Creator", "User Name", 8),
            new com.migrationreport.dto.MetadataFieldDTO("FILEFULLPATH", "FileFullPath", "File Path", 8),
            new com.migrationreport.dto.MetadataFieldDTO("UD5E8_ADDRESS", "Address", "Address", 8),
            new com.migrationreport.dto.MetadataFieldDTO("UC7A6_ORDER_NO", "OrderNo", "Order No", 8)
        );

        availableFields.addAll(fallbackList);
        for (com.migrationreport.dto.MetadataFieldDTO field : fallbackList) {
            displayNameToColumnName.put(field.getDisplayName().toLowerCase(), field.getColumnName());
        }
    }

    public List<com.migrationreport.dto.MetadataFieldDTO> getAvailableFields() {
        ensureFieldsLoaded();
        return new ArrayList<>(availableFields);
    }

    public SearchService(
            JdbcTemplate jdbcTemplate,
            QueryValidator queryValidator,
            ConfigurationService configurationService,
            @Value("${search.target-table}") String targetTable,
            @Value("${search.tables.source}") String sourceTable,
            @Value("${search.tables.staging}") String stagingTable,
            @Value("${search.tables.target}") String targetTableConfig,
            @Value("${search.system-columns.doc-id}") String docIdColumn,
            @Value("${search.system-columns.created-date}") String createdDateColumn,
            @Value("${search.system-columns.content-size}") String contentSizeColumn,
            @Value("${search.system-columns.mime-type}") String mimeTypeColumn,
            @Value("${search.status-column}") String statusColumn,
            @Value("${search.tables.source.custom-columns}") String sourceCustomColumnsStr,
            @Value("${search.tables.staging.custom-columns}") String stagingCustomColumnsStr,
            @Value("${search.tables.target.custom-columns}") String targetCustomColumnsStr,
            @Value("${search.date-column:CREATE_DATE}") String dateColumn,
            @Value("${search.report.system-properties:object_id,mime_type,content_size}") String recSystemPropertiesStr,
            @Value("${search.report.custom-metadata:*}") String recCustomMetadataStr,
            @Value("${search.report.extra-properties:}") String recExtraPropertiesStr) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryValidator = queryValidator;
        this.configurationService = configurationService;
        this.targetTable = targetTable;
        this.dateColumn = dateColumn;

        // Configurable tables
        this.sourceTable = sourceTable;
        this.stagingTable = stagingTable;
        this.targetTableConfig = targetTableConfig;

        // Configurable system metadata column mappings
        this.docIdColumn = docIdColumn;
        this.createdDateColumn = createdDateColumn;
        this.contentSizeColumn = contentSizeColumn;
        this.mimeTypeColumn = mimeTypeColumn;

        // Configurable status column
        this.statusColumn = statusColumn;

        // Parse source custom columns
        if (sourceCustomColumnsStr != null && !sourceCustomColumnsStr.trim().isEmpty()) {
            this.sourceCustomColumnsList = Arrays.stream(sourceCustomColumnsStr.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
            this.sourceCustomColumns = this.sourceCustomColumnsList.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        } else {
            this.sourceCustomColumnsList = Collections.emptyList();
            this.sourceCustomColumns = Collections.emptySet();
        }

        // Parse staging custom columns
        if (stagingCustomColumnsStr != null && !stagingCustomColumnsStr.trim().isEmpty()) {
            this.stagingCustomColumnsList = Arrays.stream(stagingCustomColumnsStr.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
            this.stagingCustomColumns = this.stagingCustomColumnsList.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        } else {
            this.stagingCustomColumnsList = Collections.emptyList();
            this.stagingCustomColumns = Collections.emptySet();
        }

        // Parse target custom columns
        if (targetCustomColumnsStr != null && !targetCustomColumnsStr.trim().isEmpty()) {
            this.targetCustomColumnsList = Arrays.stream(targetCustomColumnsStr.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
            this.targetCustomColumns = this.targetCustomColumnsList.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        } else {
            this.targetCustomColumnsList = Collections.emptyList();
            this.targetCustomColumns = Collections.emptySet();
        }

        if (recSystemPropertiesStr != null && !recSystemPropertiesStr.trim().isEmpty()) {
            this.reconciliationSystemProperties = Arrays.stream(recSystemPropertiesStr.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        } else {
            this.reconciliationSystemProperties = Arrays.asList("object_id", "mime_type", "content_size");
        }

        if (recCustomMetadataStr != null && !recCustomMetadataStr.trim().isEmpty()) {
            this.reconciliationCustomMetadata = Arrays.stream(recCustomMetadataStr.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        } else {
            this.reconciliationCustomMetadata = Collections.singletonList("*");
        }

        if (recExtraPropertiesStr != null && !recExtraPropertiesStr.trim().isEmpty()) {
            this.reconciliationExtraProperties = Arrays.stream(recExtraPropertiesStr.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        } else {
            this.reconciliationExtraProperties = Collections.emptyList();
        }
    }

    private List<String> reconciliationExtraProperties;

    public List<String> getReconciliationSystemProperties() {
        return reconciliationSystemProperties;
    }

    public List<String> getReconciliationCustomMetadata() {
        return reconciliationCustomMetadata;
    }



    public List<String> getCustomColumnsForTable(String tableKey) {
        if (tableKey == null) {
            return Collections.emptyList();
        }
        switch (tableKey.toLowerCase().trim()) {
            case "source":
                return sourceCustomColumnsList;
            case "staging":
                return stagingCustomColumnsList;
            case "target":
                return targetCustomColumnsList;
            default:
                return Collections.emptyList();
        }
    }

    public Map<String, String> getTablesConfig() {
        return Map.of(
            "source", sourceTable,
            "staging", stagingTable,
            "target", targetTableConfig
        );
    }

    public Map<String, String> getSystemColumnsConfig() {
        return Map.of(
            "doc-id", docIdColumn,
            "created-date", createdDateColumn,
            "content-size", contentSizeColumn,
            "mime-type", mimeTypeColumn
        );
    }



    public List<Map<String, Object>> searchByCustomQuery(String queryFragment) {
        queryValidator.validateQueryFragment(queryFragment);

        String sql = String.format("SELECT * FROM %s WHERE %s", targetTable, queryFragment);
        return jdbcTemplate.queryForList(sql);
    }

    private static final java.util.regex.Pattern SAFE_SQL_PATTERN =
            java.util.regex.Pattern.compile("^\\s*SELECT\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final java.util.regex.Pattern DANGEROUS_PATTERN = java.util.regex.Pattern.compile(
            "\\b(insert|update|delete|drop|alter|create|truncate|rename|replace|grant|revoke|merge|exec|execute)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    public List<Map<String, Object>> search(SearchRequest request) {
        if (request.getTable() == null || request.getTable().trim().isEmpty()) {
            throw new IllegalArgumentException("Table key parameter is required");
        }
        if (request.getAppId() == null || request.getAppId().trim().isEmpty()) {
            throw new IllegalArgumentException("AppId parameter is required");
        }

        com.migrationreport.dto.config.TenantConfig.ApplicationConfig appConfig = configurationService.getApplicationConfig(request.getAppId());
        if (appConfig == null) {
            throw new ResourceNotFoundException("Application configuration not found for appId: " + request.getAppId());
        }

        String tableKey = request.getTable().toLowerCase().trim();
        String physicalTableName = null;
        if (appConfig.getClassifiedTables() != null && appConfig.getClassifiedTables().get(tableKey) != null && !appConfig.getClassifiedTables().get(tableKey).isEmpty()) {
            physicalTableName = appConfig.getClassifiedTables().get(tableKey).get(0);
        }
        if (physicalTableName == null) {
            throw new ResourceNotFoundException("Table mapping not found for table key: " + tableKey);
        }

        String physicalTable = appConfig.getSchema() + "." + physicalTableName;
        
        String currentDocIdColumn = this.docIdColumn; // defaults to object_id
        if (appConfig.getPrimaryColumns() != null && appConfig.getPrimaryColumns().get(tableKey) != null) {
            currentDocIdColumn = appConfig.getPrimaryColumns().get(tableKey);
        } else {
            if (tableKey.equals("staging")) currentDocIdColumn = "stg_object_id";
            else if (tableKey.equals("target")) currentDocIdColumn = "p8_doc_id";
        }

        Set<String> whitelistedCustomColumns;
        switch (tableKey) {
            case "source":
                whitelistedCustomColumns = sourceCustomColumns;
                break;
            case "staging":
                whitelistedCustomColumns = stagingCustomColumns;
                break;
            case "target":
                whitelistedCustomColumns = targetCustomColumns;
                break;
            default:
                throw new IllegalArgumentException("Unsupported table key: " + tableKey);
        }

        boolean hasSystemProperty = false;
        if (request.getDocIds() != null && !request.getDocIds().isEmpty()) {
            hasSystemProperty = true;
        }
        if (request.getSystemFilters() != null) {
            for (Map.Entry<String, String> entry : request.getSystemFilters().entrySet()) {
                if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                    hasSystemProperty = true;
                    break;
                }
            }
        }

        boolean hasCustomOrDateFilter = false;
        if (request.getFromDate() != null && !request.getFromDate().isBlank()) {
            hasCustomOrDateFilter = true;
        }
        if (request.getToDate() != null && !request.getToDate().isBlank()) {
            hasCustomOrDateFilter = true;
        }
        if (request.getCustomFilters() != null) {
            for (Map.Entry<String, String> entry : request.getCustomFilters().entrySet()) {
                if (entry.getValue() != null && !entry.getValue().trim().isEmpty()) {
                    hasCustomOrDateFilter = true;
                    break;
                }
            }
        }

        if (!hasSystemProperty && !hasCustomOrDateFilter) {
            throw new IllegalArgumentException("Please fill at least one field to search.");
        }

        if (!hasSystemProperty) {
            throw new IllegalArgumentException("Please fill at least one field in System Properties to search.");
        }

        String selectCols = buildSelectClauseForTable(physicalTable, request.getTable());
        StringBuilder sql = new StringBuilder("SELECT " + selectCols + " FROM ").append(physicalTable).append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        // 1. Bulk Document IDs Search
        if (request.getDocIds() != null && !request.getDocIds().isEmpty()) {
            sql.append(" AND ").append(currentDocIdColumn).append(" IN (");
            for (int i = 0; i < request.getDocIds().size(); i++) {
                sql.append(i == 0 ? "?" : ",?");
                params.add(request.getDocIds().get(i).trim());
            }
            sql.append(")");
        }

        // 2. Status Filter (Success, failed, total)
        String selectedStatus = request.getStatus();
        String currentStatusColumn = configurationService.getSystemColumn(request.getAppId(), "status", statusColumn);
        if (selectedStatus != null && !selectedStatus.trim().isEmpty() && !selectedStatus.equalsIgnoreCase("total")) {
            sql.append(" AND ").append(currentStatusColumn).append(" = ?");
            params.add(selectedStatus.trim());
        }

        // 3. Date Range Filters
        String currentDateColumn = configurationService.getSystemColumn(request.getAppId(), "date", dateColumn);
        if (request.getFromDate() != null && !request.getFromDate().isBlank()) {
            sql.append(" AND ").append(dialect.castToTimestamp(currentDateColumn)).append(" >= ").append(dialect.castParameterToTimestamp());
            params.add(request.getFromDate().trim());
        }
        if (request.getToDate() != null && !request.getToDate().isBlank()) {
            sql.append(" AND ").append(dialect.castToTimestamp(currentDateColumn)).append(" <= ").append(dialect.castParameterToTimestamp());
            params.add(request.getToDate().trim());
        }

        // 4. System Filters
        if (request.getSystemFilters() != null) {
            for (Map.Entry<String, String> entry : request.getSystemFilters().entrySet()) {
                String key = entry.getKey().trim().toLowerCase();
                String value = entry.getValue();
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }

                String dbColumn = getSystemDbColumn(request.getAppId(), key);
                if (dbColumn != null) {
                    if (key.equals("doc-id") || key.equals("docid")) {
                        sql.append(" AND ").append(dbColumn).append(" = ?");
                        params.add(value.trim());
                    } else {
                        sql.append(" AND LOWER(CAST(").append(dbColumn).append(" AS VARCHAR(8000))) LIKE ?");
                        params.add("%" + value.trim().toLowerCase() + "%");
                    }
                }
            }
        }

        // 5. Custom Filters
        if (request.getCustomFilters() != null) {
            ensureFieldsLoaded();
            for (Map.Entry<String, String> entry : request.getCustomFilters().entrySet()) {
                String key = entry.getKey().trim();
                String value = entry.getValue();
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }

                // Resolve display name (e.g., "User Name") to column name (e.g., "UA8C8_USER_NAME")
                String resolvedColumn = displayNameToColumnName.get(key.toLowerCase());
                if (resolvedColumn == null) {
                    resolvedColumn = key; // Fallback to key direct if no mapping
                }

                String colLower = resolvedColumn.toLowerCase();

                // Validate if allowed. It is allowed if whitelisted or matches a dynamic metadata mapping
                boolean isAllowed = whitelistedCustomColumns.contains(colLower);
                if (!isAllowed) {
                    for (com.migrationreport.dto.MetadataFieldDTO field : availableFields) {
                        if (field.getColumnName() != null && field.getColumnName().toLowerCase().equals(colLower)) {
                            isAllowed = true;
                            break;
                        }
                    }
                }

                if (!isAllowed) {
                    throw new IllegalArgumentException("Column '" + key + "' (resolved as '" + resolvedColumn + "') is not whitelisted or mapped for custom searching in table: " + request.getTable());
                }

                sql.append(" AND LOWER(CAST(").append(resolvedColumn).append(" AS VARCHAR(8000))) LIKE ?");
                params.add("%" + value.trim().toLowerCase() + "%");
            }
        }
        String finalSql = sql.toString();
        log.info("Executing SQL Query: {} with params: {}", finalSql, params);
        return jdbcTemplate.queryForList(finalSql, params.toArray());
    }

    public List<Map<String, Object>> executeQuery(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL query must not be empty.");
        }
        if (!SAFE_SQL_PATTERN.matcher(sql.trim()).find()) {
            throw new IllegalArgumentException("Only SELECT statements are permitted.");
        }
        if (DANGEROUS_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("Query contains unauthorized SQL keywords.");
        }
        if (sql.contains(";")) {
            throw new IllegalArgumentException("Query chaining (semicolons) is not allowed.");
        }
        return jdbcTemplate.queryForList(sql.trim());
    }

    private String getSystemDbColumn(String appId, String systemKey) {
        switch (systemKey.toLowerCase().trim()) {
            case "doc-id":
            case "docid":
                return configurationService.getSystemColumn(appId, "doc-id", docIdColumn);
            case "created-date":
            case "createddate":
                return configurationService.getSystemColumn(appId, "created-date", createdDateColumn);
            case "content-size":
            case "contentsize":
                return configurationService.getSystemColumn(appId, "content-size", contentSizeColumn);
            case "mime-type":
            case "mimetype":
                return configurationService.getSystemColumn(appId, "mime-type", mimeTypeColumn);
            default:
                return null;
        }
    }

    public String buildSelectClauseForTable(String tableString, String tableKey) {
        String schema = "public";
        String tableName = tableString;
        if (tableString != null && tableString.contains(".")) {
            schema = tableString.substring(0, tableString.indexOf("."));
            tableName = tableString.substring(tableString.indexOf(".") + 1);
        }
        
        String sql = "SELECT LOWER(column_name) FROM information_schema.columns WHERE table_schema = ? AND table_name = ?";
        List<String> dbCols = jdbcTemplate.queryForList(sql, new Object[]{schema, tableName}, String.class);
        java.util.Set<String> dbColsSet = new java.util.HashSet<>(dbCols);
        
        List<String> cols = new ArrayList<>();
        
        if (reconciliationSystemProperties != null) {
            for (String prop : reconciliationSystemProperties) {
                if (dbColsSet.contains(prop.toLowerCase())) {
                    cols.add(prop);
                }
            }
        }
        
        if (reconciliationCustomMetadata != null && !reconciliationCustomMetadata.isEmpty()) {
            if (reconciliationCustomMetadata.contains("*")) {
                if (tableKey != null) {
                    List<String> mappedCols = getCustomColumnsForTable(tableKey);
                    for (String mc : mappedCols) {
                        if (dbColsSet.contains(mc.toLowerCase())) cols.add(mc);
                    }
                } else {
                    dbColsSet.stream()
                             .filter(dbCol -> dbCol.matches("u[0-9a-fA-F]+_.*"))
                             .sorted()
                             .forEach(cols::add);
                }
            } else {
                for (String cmd : reconciliationCustomMetadata) {
                    if (dbColsSet.contains(cmd.toLowerCase())) cols.add(cmd);
                }
            }
        }
        
        if (reconciliationExtraProperties != null) {
            for (String prop : reconciliationExtraProperties) {
                if (dbColsSet.contains(prop.toLowerCase())) {
                    cols.add(prop);
                }
            }
        }
        
        if (cols.isEmpty()) {
            return "*";
        }
        return String.join(", ", cols);
    }
}


