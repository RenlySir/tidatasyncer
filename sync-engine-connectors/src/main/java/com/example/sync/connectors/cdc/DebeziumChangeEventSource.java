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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

@Component
public class DebeziumChangeEventSource implements ChangeEventSource {

    static final String MYSQL_SERVER_ID_ALIAS = "mysqlServerId";
    static final String MYSQL_SNAPSHOT_MODE_ALIAS = "mysqlSnapshotMode";
    static final String MYSQL_BINLOG_FILENAME_ALIAS = "mysqlBinlogFilename";
    static final String MYSQL_BINLOG_POSITION_ALIAS = "mysqlBinlogPosition";
    static final String MARIADB_SERVER_ID_ALIAS = "mariaDbServerId";
    static final String MARIADB_SNAPSHOT_MODE_ALIAS = "mariaDbSnapshotMode";
    static final String ORACLE_START_SCN_ALIAS = "oracleStartScn";
    static final String ORACLE_ADAPTER_ALIAS = "oracleAdapter";
    static final String ORACLE_PDB_NAME_ALIAS = "oraclePdbName";
    static final String ORACLE_OUT_SERVER_NAME_ALIAS = "oracleOutServerName";
    static final String POSTGRES_PLUGIN_NAME_ALIAS = "postgresPluginName";
    static final String POSTGRES_PUBLICATION_AUTO_CREATE_MODE_ALIAS = "postgresPublicationAutoCreateMode";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DebeziumOffsetSeeder offsetSeeder = new DebeziumOffsetSeeder();

    @Override
    public boolean supports(SyncJobDefinition definition) {
        SourceDatabaseType type = definition.source().databaseType();
        return type == SourceDatabaseType.MYSQL
                || type == SourceDatabaseType.MARIADB
                || type == SourceDatabaseType.ORACLE
                || type == SourceDatabaseType.SQLSERVER
                || type == SourceDatabaseType.POSTGRESQL
                || type == SourceDatabaseType.DB2;
    }

    @Override
    public ChangeCaptureHandle start(SyncJobDefinition definition, ChangeEventSink sink, ProgressReporter reporter) throws Exception {
        Properties properties = buildProperties(definition);
        offsetSeeder.seedInitialOffsetIfNeeded(definition, properties, reporter);
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
        String table = textValue(source, "table");
        String catalog = textValue(source, "db");
        String schema = resolveSchema(definition, source, catalog);
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
        String logPosition = resolveLogPosition(definition.source().databaseType(), source);
        if (logPosition != null) {
            reporter.updateLogPosition(logPosition, "Incremental log position updated");
        }
        reporter.updateLatestEvent(mapped);
        sink.accept(mapped);
    }

    Properties buildProperties(SyncJobDefinition definition) {
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
        properties.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
        properties.setProperty("schema.history.internal.file.filename", definition.incremental().offsetStoragePath() + ".schema");
        properties.setProperty("table.include.list", buildTableIncludeList(definition));
        properties.setProperty("include.schema.changes", "false");

        switch (definition.source().databaseType()) {
            case MYSQL -> {
                properties.setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector");
                properties.setProperty("database.include.list", definition.source().databaseName());
                properties.setProperty("database.server.id", resolveMySqlServerId(definition));
                properties.setProperty("snapshot.mode", resolveMySqlSnapshotMode(definition));
            }
            case MARIADB -> {
                properties.setProperty("connector.class", "io.debezium.connector.mariadb.MariaDbConnector");
                properties.setProperty("database.include.list", definition.source().databaseName());
                properties.setProperty("database.server.id", resolveMariaDbServerId(definition));
                properties.setProperty("snapshot.mode", resolveMariaDbSnapshotMode(definition));
            }
            case ORACLE -> {
                properties.setProperty("connector.class", "io.debezium.connector.oracle.OracleConnector");
                properties.setProperty("database.dbname", definition.source().databaseName());
                properties.setProperty("database.connection.adapter", resolveOracleAdapter(definition));
                String pdbName = resolveOraclePdbName(definition);
                if (pdbName != null) {
                    properties.setProperty("database.pdb.name", pdbName);
                }
                String outServerName = resolveOracleOutServerName(definition);
                if (outServerName != null) {
                    properties.setProperty("database.out.server.name", outServerName);
                }
            }
            case SQLSERVER -> {
                properties.setProperty("connector.class", "io.debezium.connector.sqlserver.SqlServerConnector");
                properties.setProperty("database.names", definition.source().databaseName());
            }
            case POSTGRESQL -> {
                properties.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
                properties.setProperty("database.dbname", definition.source().databaseName());
                properties.setProperty("slot.name", definition.incremental().slotName() == null ? "sync_slot" : definition.incremental().slotName());
                properties.setProperty("publication.name", definition.incremental().publicationName() == null ? "sync_pub" : definition.incremental().publicationName());
                properties.setProperty("plugin.name", resolvePostgresPluginName(definition));
                properties.setProperty("publication.autocreate.mode", resolvePostgresPublicationAutoCreateMode(definition));
            }
            case DB2 -> {
                properties.setProperty("connector.class", "io.debezium.connector.db2.Db2Connector");
                properties.setProperty("database.dbname", definition.source().databaseName());
            }
            default -> throw new IllegalArgumentException("Unsupported Debezium source type");
        }

        if (definition.incremental().additionalProperties() != null) {
            definition.incremental().additionalProperties().forEach((key, value) -> {
                if (value == null || value.isBlank()) {
                    return;
                }
                if (MYSQL_SERVER_ID_ALIAS.equals(key)
                        || MYSQL_SNAPSHOT_MODE_ALIAS.equals(key)
                        || MYSQL_BINLOG_FILENAME_ALIAS.equals(key)
                        || MYSQL_BINLOG_POSITION_ALIAS.equals(key)
                        || MARIADB_SERVER_ID_ALIAS.equals(key)
                        || MARIADB_SNAPSHOT_MODE_ALIAS.equals(key)
                        || ORACLE_START_SCN_ALIAS.equals(key)
                        || ORACLE_ADAPTER_ALIAS.equals(key)
                        || ORACLE_PDB_NAME_ALIAS.equals(key)
                        || ORACLE_OUT_SERVER_NAME_ALIAS.equals(key)
                        || POSTGRES_PLUGIN_NAME_ALIAS.equals(key)
                        || POSTGRES_PUBLICATION_AUTO_CREATE_MODE_ALIAS.equals(key)
                        || key.startsWith("dm")) {
                    return;
                }
                properties.setProperty(key, value);
            });
        }
        return properties;
    }

    private String buildTableIncludeList(SyncJobDefinition definition) {
        return definition.tableMappings().stream()
                .map(mapping -> qualifyTable(definition, mapping))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private String qualifyTable(SyncJobDefinition definition, com.example.sync.core.config.TableMapping mapping) {
        if (definition.source().databaseType() == SourceDatabaseType.MYSQL
                || definition.source().databaseType() == SourceDatabaseType.MARIADB) {
            String database = firstNonBlank(mapping.sourceCatalog(), definition.source().databaseName(), mapping.sourceSchema());
            return database + "." + mapping.sourceTable();
        }
        if (definition.source().databaseType() == SourceDatabaseType.SQLSERVER) {
            String database = firstNonBlank(mapping.sourceCatalog(), definition.source().databaseName());
            String schema = firstNonBlank(mapping.sourceSchema(), definition.source().schemaName(), "dbo");
            return database + "." + schema + "." + mapping.sourceTable();
        }
        return firstNonBlank(mapping.sourceSchema(), definition.source().schemaName()) + "." + mapping.sourceTable();
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
        Map<String, Object> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            result.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
        }
        return result;
    }

    private String resolveSchema(SyncJobDefinition definition, JsonNode source, String catalog) {
        String schema = textValue(source, "schema");
        if (definition.source().databaseType() == SourceDatabaseType.MYSQL
                || definition.source().databaseType() == SourceDatabaseType.MARIADB) {
            return firstNonBlank(schema, catalog, definition.source().schemaName(), definition.source().databaseName());
        }
        return schema;
    }

    private String resolveLogPosition(SourceDatabaseType databaseType, JsonNode source) {
        return switch (databaseType) {
            case MYSQL -> {
                String file = textValue(source, "file");
                String position = textValue(source, "pos");
                String row = textValue(source, "row");
                if (file == null || position == null) {
                    yield null;
                }
                yield row == null ? file + ":" + position : file + ":" + position + ":" + row;
            }
            case MARIADB -> {
                String file = textValue(source, "file");
                String position = textValue(source, "pos");
                String row = textValue(source, "row");
                if (file == null || position == null) {
                    yield null;
                }
                yield row == null ? file + ":" + position : file + ":" + position + ":" + row;
            }
            case ORACLE -> {
                String commitScn = textValue(source, "commit_scn");
                String scn = textValue(source, "scn");
                yield commitScn != null ? "COMMIT_SCN=" + commitScn : (scn != null ? "SCN=" + scn : null);
            }
            case SQLSERVER -> {
                String commitLsn = textValue(source, "commit_lsn");
                String changeLsn = textValue(source, "change_lsn");
                yield commitLsn != null ? "COMMIT_LSN=" + commitLsn : (changeLsn != null ? "CHANGE_LSN=" + changeLsn : null);
            }
            case POSTGRESQL -> {
                String lsn = textValue(source, "lsn");
                yield lsn == null ? null : "LSN=" + lsn;
            }
            case DB2 -> {
                String commitLsn = textValue(source, "commit_lsn");
                String changeLsn = textValue(source, "change_lsn");
                yield commitLsn != null ? "COMMIT_LSN=" + commitLsn : (changeLsn != null ? "CHANGE_LSN=" + changeLsn : null);
            }
            default -> null;
        };
    }

    private String resolveMySqlServerId(SyncJobDefinition definition) {
        String configured = definition.incremental().additionalProperties() == null
                ? null
                : definition.incremental().additionalProperties().get(MYSQL_SERVER_ID_ALIAS);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        long jobId = definition.jobId() == null ? 1L : definition.jobId();
        return String.valueOf(5400L + (jobId % 1_000_000L));
    }

    private String resolveMySqlSnapshotMode(SyncJobDefinition definition) {
        String configured = definition.incremental().additionalProperties() == null
                ? null
                : definition.incremental().additionalProperties().get(MYSQL_SNAPSHOT_MODE_ALIAS);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "no_data";
    }

    private String resolveMariaDbServerId(SyncJobDefinition definition) {
        String configured = definition.incremental().additionalProperties() == null
                ? null
                : definition.incremental().additionalProperties().get(MARIADB_SERVER_ID_ALIAS);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        long jobId = definition.jobId() == null ? 1L : definition.jobId();
        return String.valueOf(6400L + (jobId % 1_000_000L));
    }

    private String resolveMariaDbSnapshotMode(SyncJobDefinition definition) {
        String configured = definition.incremental().additionalProperties() == null
                ? null
                : definition.incremental().additionalProperties().get(MARIADB_SNAPSHOT_MODE_ALIAS);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "no_data";
    }

    private String resolveOracleAdapter(SyncJobDefinition definition) {
        String configured = definition.incremental().additionalProperties() == null
                ? null
                : definition.incremental().additionalProperties().get(ORACLE_ADAPTER_ALIAS);
        String adapter = configured == null || configured.isBlank() ? "logminer" : configured;
        if ("xstream".equals(adapter) && resolveOracleOutServerName(definition) == null) {
            throw new IllegalArgumentException("Oracle XStream mode requires oracleOutServerName");
        }
        return adapter;
    }

    private String resolveOraclePdbName(SyncJobDefinition definition) {
        String configured = definition.incremental().additionalProperties() == null
                ? null
                : definition.incremental().additionalProperties().get(ORACLE_PDB_NAME_ALIAS);
        return configured == null || configured.isBlank() ? null : configured;
    }

    private String resolveOracleOutServerName(SyncJobDefinition definition) {
        String configured = definition.incremental().additionalProperties() == null
                ? null
                : definition.incremental().additionalProperties().get(ORACLE_OUT_SERVER_NAME_ALIAS);
        return configured == null || configured.isBlank() ? null : configured;
    }

    private String resolvePostgresPluginName(SyncJobDefinition definition) {
        String configured = definition.incremental().additionalProperties() == null
                ? null
                : definition.incremental().additionalProperties().get(POSTGRES_PLUGIN_NAME_ALIAS);
        return configured == null || configured.isBlank() ? "pgoutput" : configured;
    }

    private String resolvePostgresPublicationAutoCreateMode(SyncJobDefinition definition) {
        String configured = definition.incremental().additionalProperties() == null
                ? null
                : definition.incremental().additionalProperties().get(POSTGRES_PUBLICATION_AUTO_CREATE_MODE_ALIAS);
        return configured == null || configured.isBlank() ? "all_tables" : configured;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
