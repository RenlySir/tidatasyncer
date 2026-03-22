package com.example.sync.admin.service;

import com.example.sync.admin.domain.ConnectionProfileEntity;
import com.example.sync.admin.domain.SchemaSyncTaskEntity;
import com.example.sync.admin.domain.SchemaSyncTaskStatus;
import com.example.sync.admin.dto.SchemaSyncTaskResponse;
import com.example.sync.admin.dto.SchemaSyncTaskUpsertRequest;
import com.example.sync.admin.dto.UnsupportedTypeItemResponse;
import com.example.sync.admin.repository.SchemaSyncTaskRepository;
import com.example.sync.connectors.util.JdbcConnectionSupport;
import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.TargetConnectionProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchemaSyncTaskService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<UnsupportedTypeItemResponse>> UNSUPPORTED_LIST = new TypeReference<>() {
    };

    private final SchemaSyncTaskRepository repository;
    private final ConnectionProfileService connectionProfileService;
    private final ConnectionProfileBindingService connectionBindingService;
    private final SchemaTypeMappingService schemaTypeMappingService;
    private final ObjectMapper objectMapper;

    public SchemaSyncTaskService(
            SchemaSyncTaskRepository repository,
            ConnectionProfileService connectionProfileService,
            ConnectionProfileBindingService connectionBindingService,
            SchemaTypeMappingService schemaTypeMappingService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.connectionProfileService = connectionProfileService;
        this.connectionBindingService = connectionBindingService;
        this.schemaTypeMappingService = schemaTypeMappingService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SchemaSyncTaskResponse> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SchemaSyncTaskResponse get(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional
    public SchemaSyncTaskResponse create(SchemaSyncTaskUpsertRequest request) {
        SchemaSyncTaskEntity entity = new SchemaSyncTaskEntity();
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public SchemaSyncTaskResponse update(Long id, SchemaSyncTaskUpsertRequest request) {
        SchemaSyncTaskEntity entity = findEntity(id);
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public SchemaSyncTaskResponse execute(Long id) {
        SchemaSyncTaskEntity entity = findEntity(id);
        try {
            ConnectionProfileEntity sourceProfile = connectionBindingService.requireSourceProfile(
                    connectionProfileService.findEntity(entity.getSourceProfileId()),
                    "schema synchronization",
                    false
            );
            ConnectionProfileEntity targetProfile = connectionBindingService.requireTidbTargetProfile(
                    connectionProfileService.findEntity(entity.getTargetProfileId()),
                    "Schema synchronization"
            );
            RenderedSchema rendered = renderSchema(entity, sourceProfile, targetProfile);

            Path workDir = Path.of("work", "schema-sync", "task-" + entity.getId()).toAbsolutePath().normalize();
            Files.createDirectories(workDir);
            Path ddlPath = workDir.resolve("schema.sql");
            Files.writeString(ddlPath, rendered.ddl());
            entity.setGeneratedDdl(rendered.ddl());
            entity.setGeneratedDdlPath(ddlPath.toString());

            if (!rendered.unsupportedItems().isEmpty()) {
                Path unsupportedPath = workDir.resolve("unsupported-types.txt");
                Files.writeString(unsupportedPath, renderUnsupportedFile(rendered.unsupportedItems()));
                entity.setUnsupportedItemsJson(writeJson(rendered.unsupportedItems()));
                entity.setUnsupportedItemsPath(unsupportedPath.toString());
                entity.setStatus(SchemaSyncTaskStatus.REVIEW_REQUIRED);
                entity.setLastMessage("Unsupported source types found. Review and provide override mappings before executing again.");
                entity.setExecutedAt(Instant.now());
                return toResponse(repository.save(entity));
            }

            executeRenderedDdl(targetProfile, rendered);
            entity.setUnsupportedItemsJson(writeJson(List.of()));
            entity.setUnsupportedItemsPath(null);
            entity.setStatus(SchemaSyncTaskStatus.COMPLETED);
            entity.setLastMessage("Schema synchronization completed successfully.");
            entity.setExecutedAt(Instant.now());
            return toResponse(repository.save(entity));
        } catch (Exception ex) {
            entity.setStatus(SchemaSyncTaskStatus.FAILED);
            entity.setLastMessage(ex.getMessage());
            entity.setExecutedAt(Instant.now());
            return toResponse(repository.save(entity));
        }
    }

    public SchemaSyncTaskEntity findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schema sync task not found: " + id));
    }

    private void apply(SchemaSyncTaskEntity entity, SchemaSyncTaskUpsertRequest request) {
        entity.setName(request.name());
        entity.setSourceProfileId(request.sourceProfileId());
        entity.setTargetProfileId(request.targetProfileId());
        entity.setTableSelectionMode(request.tableSelectionMode());
        entity.setSelectedTablesJson(writeJson(request.selectedTables() == null ? List.of() : request.selectedTables()));
        entity.setOverrideMappingsJson(writeJson(request.overrideMappings() == null ? Map.of() : request.overrideMappings()));
        if (entity.getStatus() == null || entity.getStatus() == SchemaSyncTaskStatus.FAILED || entity.getStatus() == SchemaSyncTaskStatus.COMPLETED) {
            entity.setStatus(SchemaSyncTaskStatus.DRAFT);
        }
        entity.setLastMessage("Schema task saved.");
    }

    private SchemaSyncTaskResponse toResponse(SchemaSyncTaskEntity entity) {
        return new SchemaSyncTaskResponse(
                entity.getId(),
                entity.getName(),
                entity.getSourceProfileId(),
                entity.getTargetProfileId(),
                entity.getTableSelectionMode(),
                readJson(entity.getSelectedTablesJson(), STRING_LIST, List.of()),
                readJson(entity.getOverrideMappingsJson(), STRING_MAP, Map.of()),
                entity.getStatus(),
                entity.getLastMessage(),
                entity.getGeneratedDdl(),
                entity.getGeneratedDdlPath(),
                entity.getUnsupportedItemsPath(),
                readJson(entity.getUnsupportedItemsJson(), UNSUPPORTED_LIST, List.of()),
                entity.getExecutedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private RenderedSchema renderSchema(
            SchemaSyncTaskEntity entity,
            ConnectionProfileEntity sourceProfile,
            ConnectionProfileEntity targetProfile
    ) throws Exception {
        SourceConnectionProperties source = connectionBindingService.toSourceProperties(sourceProfile);
        List<String> selectedTables = readJson(entity.getSelectedTablesJson(), STRING_LIST, List.of());
        Map<String, String> overrideMappings = readJson(entity.getOverrideMappingsJson(), STRING_MAP, Map.of());

        List<TableSchemaModel> tables;
        try (Connection connection = DriverManager.getConnection(
                JdbcConnectionSupport.resolveSourceJdbcUrl(source),
                source.username(),
                source.password()
        )) {
            tables = discoverTables(sourceProfile, connection.getMetaData(), selectedTables, entity.getTableSelectionMode());
        }

        if (tables.isEmpty()) {
            throw new IllegalArgumentException("No tables found for schema synchronization.");
        }

        String targetDatabase = targetProfile.getDatabaseName();
        List<UnsupportedTypeItemResponse> unsupportedItems = new ArrayList<>();
        List<String> statements = new ArrayList<>();
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE DATABASE IF NOT EXISTS ").append(quoteIdentifier(targetDatabase)).append(";\n\n");
        statements.add("CREATE DATABASE IF NOT EXISTS " + quoteIdentifier(targetDatabase));

        for (TableSchemaModel table : tables) {
            String tableStatement = buildCreateTableStatement(
                    sourceProfile,
                    targetDatabase,
                    table,
                    overrideMappings,
                    unsupportedItems
            );
            statements.add(tableStatement);
            ddl.append(tableStatement).append(";\n\n");
        }

        return new RenderedSchema(ddl.toString(), statements, unsupportedItems);
    }

    String buildCreateTableStatement(
            ConnectionProfileEntity sourceProfile,
            String targetDatabase,
            TableSchemaModel table,
            Map<String, String> overrideMappings,
            List<UnsupportedTypeItemResponse> unsupportedItems
    ) {
        List<String> columnDefinitions = new ArrayList<>();
        for (ColumnSchemaModel column : table.columns()) {
            SchemaTypeMappingService.ColumnMappingResult mapping = schemaTypeMappingService.mapColumn(
                    sourceProfile.getDatabaseType(),
                    table.tableName(),
                    new SchemaTypeMappingService.SourceColumnDefinition(
                            column.columnName(),
                            column.typeName(),
                            column.dataType(),
                            column.columnSize(),
                            column.decimalDigits(),
                            column.nullable()
                    ),
                    overrideMappings
            );
            if (mapping.unsupportedItem() != null) {
                unsupportedItems.add(mapping.unsupportedItem());
            }
            StringBuilder columnDefinition = new StringBuilder();
            columnDefinition.append("  ").append(quoteIdentifier(column.columnName())).append(' ').append(mapping.targetType());
            if (!column.nullable()) {
                columnDefinition.append(" NOT NULL");
            }
            columnDefinitions.add(columnDefinition.toString());
        }

        boolean hasPrimaryKey = !table.primaryKeys().isEmpty();
        if (hasPrimaryKey) {
            columnDefinitions.add("  PRIMARY KEY ("
                    + table.primaryKeys().stream().map(this::quoteIdentifier).reduce((left, right) -> left + ", " + right).orElse("")
                    + ")");
        }

        StringBuilder tableStatement = new StringBuilder()
                .append("CREATE TABLE IF NOT EXISTS ")
                .append(quoteIdentifier(targetDatabase))
                .append(".")
                .append(quoteIdentifier(table.tableName()))
                .append(" (\n")
                .append(String.join(",\n", columnDefinitions))
                .append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");

        if (!hasPrimaryKey) {
            tableStatement.append(" SHARD_ROW_ID_BITS=4");
        }

        return tableStatement.toString();
    }

    private void executeRenderedDdl(ConnectionProfileEntity targetProfile, RenderedSchema rendered) throws Exception {
        TargetConnectionProperties target = connectionBindingService.toTargetProperties(targetProfile, null);
        try (Connection connection = DriverManager.getConnection(
                JdbcConnectionSupport.resolveTargetJdbcUrl(target),
                target.username(),
                target.password()
        )) {
            for (String statementSql : rendered.statements()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(statementSql);
                }
            }
        }
    }

    private List<TableSchemaModel> discoverTables(
            ConnectionProfileEntity sourceProfile,
            DatabaseMetaData metaData,
            List<String> selectedTables,
        String tableSelectionMode
    ) throws Exception {
        String catalog = connectionBindingService.tableCatalog(sourceProfile);
        String schema = connectionBindingService.tableSchema(sourceProfile);
        Set<String> requestedTables = new LinkedHashSet<>();
        for (String selectedTable : selectedTables) {
            if (selectedTable != null && !selectedTable.isBlank()) {
                requestedTables.add(selectedTable.trim().toLowerCase(Locale.ROOT));
            }
        }

        List<TableSchemaModel> tables = new ArrayList<>();
        try (ResultSet resultSet = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                String tableCatalog = resultSet.getString("TABLE_CAT");
                String tableSchema = resultSet.getString("TABLE_SCHEM");
                String tableName = resultSet.getString("TABLE_NAME");
                if (tableName == null || tableName.isBlank()) {
                    continue;
                }
                if ("SELECTED_TABLES".equalsIgnoreCase(tableSelectionMode)
                        && !matchesSelection(requestedTables, tableSchema, tableName)) {
                    continue;
                }
                tables.add(new TableSchemaModel(
                        tableCatalog,
                        tableSchema,
                        tableName,
                        loadColumns(metaData, tableCatalog, tableSchema, tableName),
                        loadPrimaryKeys(metaData, tableCatalog, tableSchema, tableName)
                ));
            }
        }
        tables.sort(Comparator.comparing(TableSchemaModel::tableName, String.CASE_INSENSITIVE_ORDER));
        return tables;
    }

    private List<ColumnSchemaModel> loadColumns(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws Exception {
        Map<Integer, ColumnSchemaModel> sorted = new LinkedHashMap<>();
        try (ResultSet columns = metaData.getColumns(catalog, schema, tableName, "%")) {
            while (columns.next()) {
                sorted.put(
                        columns.getInt("ORDINAL_POSITION"),
                        new ColumnSchemaModel(
                                columns.getString("COLUMN_NAME"),
                                columns.getString("TYPE_NAME"),
                                columns.getInt("DATA_TYPE"),
                                columns.getInt("COLUMN_SIZE"),
                                columns.getInt("DECIMAL_DIGITS"),
                                "YES".equalsIgnoreCase(columns.getString("IS_NULLABLE"))
                        )
                );
            }
        }
        return sorted.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
    }

    private List<String> loadPrimaryKeys(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws Exception {
        Map<Short, String> sorted = new LinkedHashMap<>();
        try (ResultSet primaryKeys = metaData.getPrimaryKeys(catalog, schema, tableName)) {
            while (primaryKeys.next()) {
                sorted.put(primaryKeys.getShort("KEY_SEQ"), primaryKeys.getString("COLUMN_NAME"));
            }
        }
        return sorted.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
    }

    private String renderUnsupportedFile(List<UnsupportedTypeItemResponse> unsupportedItems) {
        StringBuilder builder = new StringBuilder();
        for (UnsupportedTypeItemResponse item : unsupportedItems) {
            builder.append(item.tableName())
                    .append('.')
                    .append(item.columnName())
                    .append(" | sourceType=")
                    .append(item.sourceType())
                    .append(" | suggestedTargetType=")
                    .append(item.suggestedTargetType())
                    .append(" | reason=")
                    .append(item.reason())
                    .append('\n');
        }
        return builder.toString();
    }

    private boolean matchesSelection(Set<String> selectedTables, String tableSchema, String tableName) {
        if (selectedTables.isEmpty()) {
            return false;
        }
        String shortName = tableName.toLowerCase(Locale.ROOT);
        String schemaQualified = (firstNonBlank(tableSchema, "") + "." + tableName).toLowerCase(Locale.ROOT);
        return selectedTables.contains(shortName) || selectedTables.contains(schemaQualified);
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private String firstNonBlank(String... values) {
        return connectionBindingService.firstNonBlank(values);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize schema sync payload", ex);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type, T defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize schema sync payload", ex);
        }
    }

    record RenderedSchema(
            String ddl,
            List<String> statements,
            List<UnsupportedTypeItemResponse> unsupportedItems
    ) {
    }

    record TableSchemaModel(
            String tableCatalog,
            String tableSchema,
            String tableName,
            List<ColumnSchemaModel> columns,
            List<String> primaryKeys
    ) {
    }

    record ColumnSchemaModel(
            String columnName,
            String typeName,
            int dataType,
            int columnSize,
            int decimalDigits,
            boolean nullable
    ) {
    }
}
