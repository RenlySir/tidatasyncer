package com.example.sync.connectors.cdc;

import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.spi.ProgressReporter;
import io.debezium.config.Configuration;
import io.debezium.connector.mysql.MySqlConnectorConfig;
import io.debezium.connector.mysql.MySqlOffsetContext;
import io.debezium.connector.mysql.MySqlPartition;
import io.debezium.connector.oracle.CommitScn;
import io.debezium.connector.oracle.OraclePartition;
import org.apache.kafka.connect.json.JsonConverter;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

final class DebeziumOffsetSeeder {

    void seedInitialOffsetIfNeeded(SyncJobDefinition definition, Properties properties, ProgressReporter reporter) throws IOException {
        OffsetSeed seed = resolveSeed(definition, properties);
        if (seed == null) {
            return;
        }

        Path offsetPath = Path.of(definition.incremental().offsetStoragePath());
        if (Files.exists(offsetPath) && Files.size(offsetPath) > 0) {
            reporter.log("INFO", "Offset file already exists, configured incremental start position is ignored: " + seed.description());
            return;
        }

        Path parent = offsetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        writeOffsetFile(offsetPath, seed);
        reporter.log("INFO", "Seeded initial incremental position: " + seed.description());
    }

    OffsetSeed resolveSeed(SyncJobDefinition definition, Properties properties) {
        if (definition.incremental().additionalProperties() == null || definition.incremental().additionalProperties().isEmpty()) {
            return null;
        }

        return switch (definition.source().databaseType()) {
            case MYSQL -> buildMySqlSeed(definition, properties);
            case MARIADB -> buildMySqlSeed(definition, properties);
            case ORACLE -> buildOracleSeed(definition, properties);
            default -> null;
        };
    }

    private OffsetSeed buildMySqlSeed(SyncJobDefinition definition, Properties properties) {
        String binlogFilename = trimToNull(definition.incremental().additionalProperties()
                .get(DebeziumChangeEventSource.MYSQL_BINLOG_FILENAME_ALIAS));
        String binlogPositionText = trimToNull(definition.incremental().additionalProperties()
                .get(DebeziumChangeEventSource.MYSQL_BINLOG_POSITION_ALIAS));

        if (binlogFilename == null && binlogPositionText == null) {
            return null;
        }
        if (binlogFilename == null || binlogPositionText == null) {
            throw new IllegalArgumentException("MySQL incremental start requires both binlog filename and position");
        }

        long binlogPosition;
        try {
            binlogPosition = Long.parseLong(binlogPositionText);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("MySQL binlog position must be numeric: " + binlogPositionText, ex);
        }

        MySqlOffsetContext offsetContext = MySqlOffsetContext.initial(new MySqlConnectorConfig(Configuration.from(properties)));
        offsetContext.setBinlogStartPoint(binlogFilename, binlogPosition);

        Map<String, Object> partition = new LinkedHashMap<>(
                new MySqlPartition(properties.getProperty("topic.prefix"), "0").getSourcePartition()
        );
        Map<String, Object> offset = new LinkedHashMap<>();
        offsetContext.getOffset().forEach(offset::put);

        return new OffsetSeed(
                properties.getProperty("name"),
                partition,
                offset,
                "MySQL binlog " + binlogFilename + ":" + binlogPosition
        );
    }

    private OffsetSeed buildOracleSeed(SyncJobDefinition definition, Properties properties) {
        String startScn = trimToNull(definition.incremental().additionalProperties()
                .get(DebeziumChangeEventSource.ORACLE_START_SCN_ALIAS));
        if (startScn == null) {
            return null;
        }

        long scnValue;
        try {
            scnValue = Long.parseLong(startScn);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Oracle SCN must be numeric: " + startScn, ex);
        }

        Map<String, Object> partition = new LinkedHashMap<>(
                new OraclePartition(properties.getProperty("topic.prefix"), "0").getSourcePartition()
        );
        Map<String, Object> offset = new LinkedHashMap<>();
        offset.put("scn", startScn);
        CommitScn.valueOf(scnValue).store(offset);

        return new OffsetSeed(
                properties.getProperty("name"),
                partition,
                offset,
                "Oracle SCN " + startScn
        );
    }

    private void writeOffsetFile(Path offsetPath, OffsetSeed seed) throws IOException {
        Map<byte[], byte[]> storedOffsets = new HashMap<>();
        storedOffsets.put(serializeKey(seed.namespace(), seed.partition()), serializeValue(seed.namespace(), seed.offset()));

        try (ObjectOutputStream outputStream = new ObjectOutputStream(Files.newOutputStream(offsetPath))) {
            outputStream.writeObject(storedOffsets);
        }
    }

    private byte[] serializeKey(String namespace, Map<String, Object> partition) {
        JsonConverter converter = newJsonConverter(true);
        List<Object> key = Arrays.asList(namespace, partition);
        return converter.fromConnectData(namespace, null, key);
    }

    private byte[] serializeValue(String namespace, Map<String, Object> offset) {
        JsonConverter converter = newJsonConverter(false);
        return converter.fromConnectData(namespace, null, offset);
    }

    private JsonConverter newJsonConverter(boolean keyConverter) {
        JsonConverter converter = new JsonConverter();
        converter.configure(Map.of("schemas.enable", false), keyConverter);
        return converter;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    record OffsetSeed(
            String namespace,
            Map<String, Object> partition,
            Map<String, Object> offset,
            String description
    ) {
    }
}
