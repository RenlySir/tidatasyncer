package com.example.sync.connectors.cdc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sync.core.config.FullLoadConfig;
import com.example.sync.core.config.IncrementalConfig;
import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.config.TargetConnectionProperties;
import com.example.sync.core.model.DeploymentArchitecture;
import com.example.sync.core.model.JobPhase;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.model.SyncMode;
import com.example.sync.core.runtime.StandardChangeEvent;
import com.example.sync.core.spi.ProgressReporter;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.json.JsonConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebeziumOffsetSeederTest {

    private final DebeziumChangeEventSource source = new DebeziumChangeEventSource();
    private final DebeziumOffsetSeeder seeder = new DebeziumOffsetSeeder();

    @TempDir
    Path tempDir;

    @Test
    void shouldSeedMySqlInitialOffsetFromBinlogPosition() throws Exception {
        Path offsetFile = tempDir.resolve("mysql-offset.dat");
        SyncJobDefinition definition = mysqlJob(offsetFile, Map.of(
                DebeziumChangeEventSource.MYSQL_SERVER_ID_ALIAS, "6601",
                DebeziumChangeEventSource.MYSQL_SNAPSHOT_MODE_ALIAS, "no_data",
                DebeziumChangeEventSource.MYSQL_BINLOG_FILENAME_ALIAS, "mysql-bin.000123",
                DebeziumChangeEventSource.MYSQL_BINLOG_POSITION_ALIAS, "456789"
        ));

        Properties properties = source.buildProperties(definition);
        seeder.seedInitialOffsetIfNeeded(definition, properties, new NoopReporter());

        DecodedOffset decoded = decodeOffset(offsetFile, properties.getProperty("name"));
        assertEquals("job-1201", decoded.namespace());
        assertEquals("sync_server", decoded.partition().get("server"));
        assertEquals("mysql-bin.000123", decoded.offset().get("file"));
        assertEquals("456789", String.valueOf(decoded.offset().get("pos")));
    }

    @Test
    void shouldSeedOracleInitialOffsetFromScn() throws Exception {
        Path offsetFile = tempDir.resolve("oracle-offset.dat");
        SyncJobDefinition definition = oracleJob(offsetFile, Map.of(
                DebeziumChangeEventSource.ORACLE_START_SCN_ALIAS, "123456789"
        ));

        Properties properties = source.buildProperties(definition);
        seeder.seedInitialOffsetIfNeeded(definition, properties, new NoopReporter());

        DecodedOffset decoded = decodeOffset(offsetFile, properties.getProperty("name"));
        assertEquals("job-2201", decoded.namespace());
        assertEquals("sync_server", decoded.partition().get("server"));
        assertEquals("123456789", String.valueOf(decoded.offset().get("scn")));
        assertTrue(decoded.offset().containsKey("commit_scn"));
        assertNotNull(decoded.offset().get("commit_scn"));
    }

    @SuppressWarnings("unchecked")
    private DecodedOffset decodeOffset(Path offsetFile, String namespace) throws Exception {
        assertTrue(Files.exists(offsetFile));
        try (ObjectInputStream inputStream = new ObjectInputStream(Files.newInputStream(offsetFile))) {
            Map<byte[], byte[]> raw = (Map<byte[], byte[]>) inputStream.readObject();
            Iterator<Map.Entry<byte[], byte[]>> iterator = raw.entrySet().iterator();
            Map.Entry<byte[], byte[]> entry = iterator.next();

            JsonConverter keyConverter = new JsonConverter();
            keyConverter.configure(Map.of("schemas.enable", false), true);
            JsonConverter valueConverter = new JsonConverter();
            valueConverter.configure(Map.of("schemas.enable", false), false);

            SchemaAndValue keyValue = keyConverter.toConnectData(namespace, entry.getKey());
            SchemaAndValue offsetValue = valueConverter.toConnectData(namespace, entry.getValue());

            List<?> keyPayload = (List<?>) keyValue.value();
            return new DecodedOffset(
                    String.valueOf(keyPayload.get(0)),
                    (Map<String, Object>) keyPayload.get(1),
                    (Map<String, Object>) offsetValue.value()
            );
        }
    }

    private SyncJobDefinition mysqlJob(Path offsetFile, Map<String, String> additionalProperties) {
        return new SyncJobDefinition(
                1201L,
                "mysql-to-tidb",
                SyncMode.FULL_AND_INCREMENTAL,
                DeploymentArchitecture.AMD64,
                new SourceConnectionProperties(
                        SourceDatabaseType.MYSQL,
                        "127.0.0.1",
                        3306,
                        "source_db",
                        "source_db",
                        "root",
                        "root",
                        "jdbc:mysql://127.0.0.1:3306/source_db",
                        "",
                        null
                ),
                new TargetConnectionProperties(
                        "127.0.0.1",
                        4000,
                        "target_db",
                        "root",
                        "root",
                        "jdbc:mysql://127.0.0.1:4000/target_db",
                        "",
                        "tidb-lightning"
                ),
                List.of(new TableMapping(
                        "source_db",
                        "source_db",
                        "orders",
                        "target_db",
                        "orders",
                        List.of("id"),
                        "updated_at",
                        List.of("id", "updated_at"),
                        Map.of()
                )),
                new FullLoadConfig(
                        "dumpling",
                        "./work/export",
                        1000,
                        1,
                        Map.of()
                ),
                new IncrementalConfig(
                        "sync_server",
                        "",
                        "",
                        offsetFile.toString(),
                        5,
                        500,
                        additionalProperties
                )
        );
    }

    private SyncJobDefinition oracleJob(Path offsetFile, Map<String, String> additionalProperties) {
        return new SyncJobDefinition(
                2201L,
                "oracle-to-tidb",
                SyncMode.FULL_AND_INCREMENTAL,
                DeploymentArchitecture.AMD64,
                new SourceConnectionProperties(
                        SourceDatabaseType.ORACLE,
                        "127.0.0.1",
                        1521,
                        "ORCLCDB",
                        "APP",
                        "system",
                        "oracle",
                        "jdbc:oracle:thin:@127.0.0.1:1521/ORCLCDB",
                        "",
                        null
                ),
                new TargetConnectionProperties(
                        "127.0.0.1",
                        4000,
                        "target_db",
                        "root",
                        "root",
                        "jdbc:mysql://127.0.0.1:4000/target_db",
                        "",
                        "tidb-lightning"
                ),
                List.of(new TableMapping(
                        "",
                        "APP",
                        "ORDERS",
                        "target_db",
                        "orders",
                        List.of("ID"),
                        "UPDATED_AT",
                        List.of("ID", "UPDATED_AT"),
                        Map.of()
                )),
                new FullLoadConfig(
                        "sqluldr2",
                        "./work/export",
                        1000,
                        1,
                        Map.of()
                ),
                new IncrementalConfig(
                        "sync_server",
                        "",
                        "",
                        offsetFile.toString(),
                        5,
                        500,
                        additionalProperties
                )
        );
    }

    private record DecodedOffset(
            String namespace,
            Map<String, Object> partition,
            Map<String, Object> offset
    ) {
    }

    private static final class NoopReporter implements ProgressReporter {

        @Override
        public void updatePhase(JobPhase phase, int percent, String message) {
        }

        @Override
        public void updateLag(long lagMillis, String message) {
        }

        @Override
        public void updateLatestEvent(StandardChangeEvent event) {
        }

        @Override
        public void log(String level, String message) {
        }

        @Override
        public boolean isStopRequested() {
            return false;
        }
    }
}
