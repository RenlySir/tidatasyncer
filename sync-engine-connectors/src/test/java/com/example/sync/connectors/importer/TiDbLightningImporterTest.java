package com.example.sync.connectors.importer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sync.core.config.FullLoadConfig;
import com.example.sync.core.config.IncrementalConfig;
import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.config.TargetConnectionProperties;
import com.example.sync.core.model.DeploymentArchitecture;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.model.SyncMode;
import com.example.sync.core.spi.CsvExportResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TiDbLightningImporterTest {

    private final TiDbLightningImporter importer = new TiDbLightningImporter();

    @Test
    void shouldBuildSqlImportConfigForMySqlDumplingOutput() {
        String config = importer.buildConfig(jobDefinition(), new CsvExportResult(
                Path.of("/tmp/export"),
                List.of(
                        Path.of("source_db-schema-create.sql"),
                        Path.of("source_db.orders-schema.sql"),
                        Path.of("source_db.orders.000000000.sql")
                ),
                0L
        ));

        assertTrue(config.contains("data-source-dir = \"/tmp/export\""));
        assertFalse(config.contains("csv.header = true"));
        assertTrue(config.contains("pattern = '^source_db-schema-create\\.sql$'"));
        assertTrue(config.contains("pattern = '^source_db\\.orders-schema\\.sql$'"));
        assertTrue(config.contains("pattern = '^source_db\\.orders(?:\\.[0-9]+)?\\.sql$'"));
        assertTrue(config.contains("schema = 'target_db'"));
        assertTrue(config.contains("table = 'orders_archive'"));
        assertTrue(config.contains("type = 'sql'"));
    }

    private SyncJobDefinition jobDefinition() {
        return new SyncJobDefinition(
                1L,
                "mysql-full-load",
                SyncMode.FULL_ONLY,
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
                        "orders_archive",
                        List.of("id"),
                        "updated_at",
                        List.of("id", "status"),
                        Map.of()
                )),
                new FullLoadConfig(
                        "dumpling",
                        "./work/export",
                        1000,
                        4,
                        Map.of()
                ),
                new IncrementalConfig(
                        "sync_server",
                        "",
                        "",
                        "./work/offsets/offset.dat",
                        5,
                        500,
                        Map.of()
                )
        );
    }
}
