package com.example.sync.connectors.sink;

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
import java.util.HashMap;
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
                definition.target().jdbcUrl(),
                definition.target().username(),
                definition.target().password()
        );
        this.connection.setAutoCommit(true);
    }

    @Override
    public void accept(StandardChangeEvent event) throws Exception {
        TableMapping mapping = resolveMapping(event);
        if (mapping == null) {
            reporter.log("WARN", "No table mapping found for " + event.sourceSchema() + "." + event.sourceTable());
            return;
        }

        if (event.operation() == ChangeOperation.DELETE) {
            executeDelete(mapping, event.keyValues());
            return;
        }

        Map<String, Object> data = new HashMap<>(Objects.requireNonNullElse(event.afterValues(), Map.of()));
        if (data.isEmpty()) {
            reporter.log("WARN", "Skipping empty payload for " + event.sourceTable());
            return;
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
                .filter(mapping -> Objects.equals(mapping.sourceSchema(), event.sourceSchema()))
                .filter(mapping -> Objects.equals(mapping.sourceTable(), event.sourceTable()))
                .findFirst()
                .orElse(null);
    }

    private void executeUpsert(TableMapping mapping, Map<String, Object> payload) throws Exception {
        List<String> sourceColumns = new ArrayList<>(payload.keySet());
        List<String> targetColumns = sourceColumns.stream().map(column -> mapColumn(mapping, column)).toList();
        String placeholders = sourceColumns.stream().map(column -> "?").collect(Collectors.joining(","));
        String updates = targetColumns.stream()
                .map(column -> column + "=VALUES(" + column + ")")
                .collect(Collectors.joining(","));
        String sql = "INSERT INTO " + mapping.targetDatabase() + "." + mapping.targetTable()
                + " (" + String.join(",", targetColumns) + ") VALUES (" + placeholders + ") "
                + "ON DUPLICATE KEY UPDATE " + updates;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < sourceColumns.size(); i++) {
                statement.setObject(i + 1, payload.get(sourceColumns.get(i)));
            }
            statement.executeUpdate();
        }
    }

    private void executeDelete(TableMapping mapping, Map<String, Object> keyValues) throws Exception {
        if (keyValues == null || keyValues.isEmpty()) {
            reporter.log("WARN", "Skipping delete without key values for " + mapping.targetTable());
            return;
        }
        List<String> keys = new ArrayList<>(keyValues.keySet());
        String conditions = keys.stream().map(key -> mapColumn(mapping, key) + "=?").collect(Collectors.joining(" AND "));
        String sql = "DELETE FROM " + mapping.targetDatabase() + "." + mapping.targetTable() + " WHERE " + conditions;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < keys.size(); i++) {
                statement.setObject(i + 1, keyValues.get(keys.get(i)));
            }
            statement.executeUpdate();
        }
    }

    private String mapColumn(TableMapping mapping, String sourceColumn) {
        if (mapping.columnMappings() == null || !mapping.columnMappings().containsKey(sourceColumn)) {
            return sourceColumn;
        }
        return mapping.columnMappings().get(sourceColumn);
    }
}
