package com.example.sync.connectors.cdc;

import com.example.sync.connectors.util.SimpleChangeCaptureHandle;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.ChangeOperation;
import com.example.sync.core.model.JobPhase;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.runtime.StandardChangeEvent;
import com.example.sync.core.spi.ChangeCaptureHandle;
import com.example.sync.core.spi.ChangeEventSink;
import com.example.sync.core.spi.ChangeEventSource;
import com.example.sync.core.spi.ProgressReporter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

@Component
public class DebeziumChangeEventSource implements ChangeEventSource {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(SyncJobDefinition definition) {
        SourceDatabaseType type = definition.source().databaseType();
        return type == SourceDatabaseType.MYSQL
                || type == SourceDatabaseType.ORACLE
                || type == SourceDatabaseType.SQLSERVER
                || type == SourceDatabaseType.POSTGRESQL;
    }

    @Override
    public ChangeCaptureHandle start(SyncJobDefinition definition, ChangeEventSink sink, ProgressReporter reporter) throws Exception {
        Properties properties = buildProperties(definition);
        CountDownLatch stopLatch = new CountDownLatch(1);

        DebeziumEngine<ChangeEvent<String, String>> engine = DebeziumEngine.create(Json.class)
                .using(properties)
                .notifying(event -> {
                    try {
                        handleEvent(definition, sink, reporter, event);
                    } catch (Exception ex) {
                        throw new IllegalStateException("Failed to process CDC event", ex);
                    }
                })
                .using((success, message, error) -> {
                    if (!success && error != null) {
                        reporter.log("ERROR", "Debezium engine error: " + error.getMessage());
                    }
                    stopLatch.countDown();
                })
                .build();

        ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "debezium-job-" + definition.jobId()));
        var future = executorService.submit(() -> {
            reporter.updatePhase(JobPhase.RUNNING_INCREMENTAL, 100, "Incremental CDC started");
            engine.run();
        });

        return new SimpleChangeCaptureHandle(executorService, future, () -> {
            try {
                engine.close();
            } catch (Exception ignored) {
            }
        }, stopLatch);
    }

    private void handleEvent(
            SyncJobDefinition definition,
            ChangeEventSink sink,
            ProgressReporter reporter,
            ChangeEvent<String, String> event
    ) throws Exception {
        if (event.value() == null || event.value().isBlank()) {
            return;
        }
        JsonNode root = objectMapper.readTree(event.value());
        JsonNode payload = root.path("payload");
        if (payload.isMissingNode()) {
            return;
        }
        JsonNode source = payload.path("source");
        String schema = textValue(source, "schema");
        String table = textValue(source, "table");
        String catalog = textValue(source, "db");
        long tsMs = payload.path("ts_ms").asLong(System.currentTimeMillis());
        Instant eventTime = Instant.ofEpochMilli(tsMs);

        StandardChangeEvent mapped = new StandardChangeEvent(
                catalog,
                schema,
                table,
                parseKeyValues(event.key()),
                parseObject(payload.path("before")),
                parseObject(payload.path("after")),
                mapOperation(payload.path("op").asText()),
                eventTime,
                Instant.now()
        );
        reporter.updateLag(Math.max(0L, Instant.now().toEpochMilli() - tsMs), "CDC lag updated");
        reporter.updateLatestEvent(mapped);
        sink.accept(mapped);
    }

    private Properties buildProperties(SyncJobDefinition definition) {
        Properties properties = new Properties();
        properties.setProperty("name", "job-" + definition.jobId());
        properties.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        properties.setProperty("offset.storage.file.filename", definition.incremental().offsetStoragePath());
        properties.setProperty("offset.flush.interval.ms", "5000");
        properties.setProperty("topic.prefix", definition.incremental().serverName());
        properties.setProperty("database.hostname", definition.source().host());
        properties.setProperty("database.port", String.valueOf(definition.source().port()));
        properties.setProperty("database.user", definition.source().username());
        properties.setProperty("database.password", definition.source().password());
        properties.setProperty("database.dbname", definition.source().databaseName());
        properties.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
        properties.setProperty("schema.history.internal.file.filename", definition.incremental().offsetStoragePath() + ".schema");
        properties.setProperty("table.include.list", buildTableIncludeList(definition));

        switch (definition.source().databaseType()) {
            case MYSQL -> properties.setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector");
            case ORACLE -> properties.setProperty("connector.class", "io.debezium.connector.oracle.OracleConnector");
            case SQLSERVER -> properties.setProperty("connector.class", "io.debezium.connector.sqlserver.SqlServerConnector");
            case POSTGRESQL -> {
                properties.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
                properties.setProperty("slot.name", definition.incremental().slotName() == null ? "sync_slot" : definition.incremental().slotName());
                properties.setProperty("publication.name", definition.incremental().publicationName() == null ? "sync_pub" : definition.incremental().publicationName());
            }
            default -> throw new IllegalArgumentException("Unsupported Debezium source type");
        }

        if (definition.incremental().additionalProperties() != null) {
            definition.incremental().additionalProperties().forEach(properties::setProperty);
        }
        return properties;
    }

    private String buildTableIncludeList(SyncJobDefinition definition) {
        return definition.tableMappings().stream()
                .map(mapping -> mapping.sourceSchema() + "." + mapping.sourceTable())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private ChangeOperation mapOperation(String op) {
        return switch (op) {
            case "c", "r" -> ChangeOperation.INSERT;
            case "u" -> ChangeOperation.UPDATE;
            case "d" -> ChangeOperation.DELETE;
            default -> ChangeOperation.SNAPSHOT;
        };
    }

    private Map<String, Object> parseKeyValues(String keyJson) throws Exception {
        if (keyJson == null || keyJson.isBlank()) {
            return Map.of();
        }
        return parseObject(objectMapper.readTree(keyJson).path("payload"));
    }

    private Map<String, Object> parseObject(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            result.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
        }
        return result;
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
