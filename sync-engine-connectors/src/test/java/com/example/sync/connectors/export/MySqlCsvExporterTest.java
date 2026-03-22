package com.example.sync.connectors.export;

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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MySqlCsvExporterTest {

    private final MySqlCsvExporter exporter = new MySqlCsvExporter();

    @Test
    void shouldUseDumplingSqlExportWith128MiBFiles() {
        String command = exporter.defaultCommandTemplate(jobDefinition());

        assertTrue(exporter.defaultExportBinary().equals("dumpling"));
        assertTrue(command.contains("--filetype sql"));
        assertTrue(command.contains("--consistency snapshot"));
        assertTrue(command.contains("-F 128MiB"));
        assertTrue(command.contains("-T ${mysqlTable}"));
        assertTrue(command.contains("-o '${outputDir}'"));
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
                        "orders",
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
