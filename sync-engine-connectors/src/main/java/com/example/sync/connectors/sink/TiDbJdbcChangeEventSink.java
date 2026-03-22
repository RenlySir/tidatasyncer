package com.example.sync.connectors.sink;

import com.example.sync.connectors.util.JdbcConnectionSupport;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.model.ChangeOperation;
import com.example.sync.core.runtime.StandardChangeEvent;
import com.example.sync.core.spi.ChangeEventSink;
import com.example.sync.core.spi.ProgressReporter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class TiDbJdbcChangeEventSink implements ChangeEventSink {

    private Connection connection;
    private SyncJobDefinition definition;
    private ProgressReporter reporter;

    @Override
    public void open(SyncJobDefinition definition, ProgressReporter reporter) throws Exception {
        this.definition = definition;
        this.reporter = reporter;
        this.connection = DriverManager.getConnection(
                JdbcConnectionSupport.resolveTargetJdbcUrl(definition.target()),
                definition.target().username(),
                definition.target().password()
        );
        this.connection.setAutoCommit(true);
    }

    @Override
    public void accept(StandardChangeEvent event) throws Exception {
        TableMapping mapping = resolveMapping(event);
        if (mapping == null) {
            reporter.log("WARN", "No table mapping found for " + event.sourceCatalog() + "." + event.sourceTable());
            return;
        }

        if (event.operation() == ChangeOperation.DELETE) {
            executeDelete(mapping, resolveDeleteKeys(mapping, event));
            return;
        }

        Map<String, Object> data = new LinkedHashMap<>(Objects.requireNonNullElse(event.afterValues(), Map.of()));
        if (data.isEmpty()) {
            reporter.log("WARN", "Skipping empty payload for " + event.sourceTable());
            return;
        }

        if (event.operation() == ChangeOperation.UPDATE && hasIdentityChange(mapping, event.beforeValues(), event.afterValues())) {
            executeDelete(mapping, extractPrimaryKeyValues(mapping, event.beforeValues()));
        }
        executeUpsert(mapping, data);
    }

    @Override
    public void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    private TableMapping resolveMapping(StandardChangeEvent event) {
        return definition.tableMappings().stream()
                .filter(mapping -> matchesSourceCatalog(mapping, event))
                .filter(mapping -> matchesSourceSchema(mapping, event))
                .filter(mapping -> Objects.equals(mapping.sourceTable(), event.sourceTable()))
                .findFirst()
                .orElse(null);
    }

    private void executeUpsert(TableMapping mapping, Map<String, Object> payload) throws Exception {
        SqlStatement upsert = buildUpsertStatement(mapping, payload);
        try (PreparedStatement statement = connection.prepareStatement(upsert.sql())) {
            bindArgs(statement, upsert.args());
            statement.executeUpdate();
        }
    }

    SqlStatement buildUpsertStatement(TableMapping mapping, Map<String, Object> payload) {
        List<String> sourceColumns = payload.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<String> targetColumns = sourceColumns.stream().map(column -> mapColumn(mapping, column)).toList();
        String placeholders = sourceColumns.stream().map(column -> "?").collect(Collectors.joining(","));
        String updates = targetColumns.stream()
                .map(column -> quoteIdentifier(column) + "=VALUES(" + quoteIdentifier(column) + ")")
                .collect(Collectors.joining(","));
        String sql = "INSERT INTO " + qualifyTable(mapping.targetDatabase(), mapping.targetTable())
                + " (" + targetColumns.stream().map(this::quoteIdentifier).collect(Collectors.joining(",")) + ") VALUES (" + placeholders + ") "
                + "ON DUPLICATE KEY UPDATE " + updates;
        List<Object> args = sourceColumns.stream().map(payload::get).toList();
        return new SqlStatement(sql, args);
    }

    private void executeDelete(TableMapping mapping, Map<String, Object> keyValues) throws Exception {
        SqlStatement delete = buildDeleteStatement(mapping, keyValues);
        if (delete == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(delete.sql())) {
            bindArgs(statement, delete.args());
            statement.executeUpdate();
        }
    }

    SqlStatement buildDeleteStatement(TableMapping mapping, Map<String, Object> keyValues) {
        if (keyValues == null || keyValues.isEmpty()) {
            reporter.log("WARN", "Skipping delete without key values for " + mapping.targetTable());
            return null;
        }
        List<String> keys = resolveDeleteColumns(mapping, keyValues);
        if (keys.isEmpty()) {
            reporter.log("WARN", "Skipping delete without usable key columns for " + mapping.targetTable());
            return null;
        }
        String conditions = keys.stream()
                .map(key -> quoteIdentifier(mapColumn(mapping, key)) + "=?")
                .collect(Collectors.joining(" AND "));
        String sql = "DELETE FROM " + qualifyTable(mapping.targetDatabase(), mapping.targetTable()) + " WHERE " + conditions;
        List<Object> args = keys.stream().map(keyValues::get).toList();
        return new SqlStatement(sql, args);
    }

    private String mapColumn(TableMapping mapping, String sourceColumn) {
        if (mapping.columnMappings() == null || !mapping.columnMappings().containsKey(sourceColumn)) {
            return sourceColumn;
        }
        return mapping.columnMappings().get(sourceColumn);
    }

    private Map<String, Object> resolveDeleteKeys(TableMapping mapping, StandardChangeEvent event) {
        Map<String, Object> explicitKeys = event.keyValues();
        if (explicitKeys != null && !explicitKeys.isEmpty()) {
            return explicitKeys;
        }
        return extractPrimaryKeyValues(mapping, event.beforeValues());
    }

    Map<String, Object> extractPrimaryKeyValues(TableMapping mapping, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        if (mapping.primaryKeys() == null || mapping.primaryKeys().isEmpty()) {
            return values;
        }
        Map<String, Object> keyValues = new LinkedHashMap<>();
        for (String primaryKey : mapping.primaryKeys()) {
            if (values.containsKey(primaryKey)) {
                keyValues.put(primaryKey, values.get(primaryKey));
            }
        }
        return keyValues;
    }

    boolean hasIdentityChange(TableMapping mapping, Map<String, Object> beforeValues, Map<String, Object> afterValues) {
        if (mapping.primaryKeys() == null || mapping.primaryKeys().isEmpty() || beforeValues == null || afterValues == null) {
            return false;
        }
        for (String primaryKey : mapping.primaryKeys()) {
            if (!Objects.equals(beforeValues.get(primaryKey), afterValues.get(primaryKey))) {
                return true;
            }
        }
        return false;
    }

    private List<String> resolveDeleteColumns(TableMapping mapping, Map<String, Object> keyValues) {
        if (mapping.primaryKeys() != null && !mapping.primaryKeys().isEmpty()) {
            List<String> keys = mapping.primaryKeys().stream()
                    .filter(keyValues::containsKey)
                    .toList();
            if (!keys.isEmpty()) {
                return keys;
            }
        }
        return keyValues.keySet().stream().sorted(Comparator.naturalOrder()).toList();
    }

    private boolean matchesSourceCatalog(TableMapping mapping, StandardChangeEvent event) {
        if (mapping.sourceCatalog() == null || mapping.sourceCatalog().isBlank()) {
            return true;
        }
        return Objects.equals(mapping.sourceCatalog(), event.sourceCatalog())
                || Objects.equals(mapping.sourceCatalog(), event.sourceSchema());
    }

    private boolean matchesSourceSchema(TableMapping mapping, StandardChangeEvent event) {
        if (mapping.sourceSchema() == null || mapping.sourceSchema().isBlank()) {
            return true;
        }
        return Objects.equals(mapping.sourceSchema(), event.sourceSchema())
                || Objects.equals(mapping.sourceSchema(), event.sourceCatalog());
    }

    private String qualifyTable(String database, String table) {
        return quoteIdentifier(database) + "." + quoteIdentifier(table);
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private void bindArgs(PreparedStatement statement, List<Object> args) throws Exception {
        for (int i = 0; i < args.size(); i++) {
            statement.setObject(i + 1, args.get(i));
        }
    }

    record SqlStatement(String sql, List<Object> args) {
    }
}
