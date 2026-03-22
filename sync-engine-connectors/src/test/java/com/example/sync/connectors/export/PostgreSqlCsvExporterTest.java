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

class PostgreSqlCsvExporterTest {

    private final PostgreSqlCsvExporter exporter = new PostgreSqlCsvExporter();

    @Test
    void shouldUsePsqlCopyByDefault() {
        String command = exporter.defaultCommandTemplate(jobDefinition(Map.of()));

        assertTrue(command.contains("\\\\copy"));
        assertTrue(command.contains("encoding 'UTF8'"));
    }

    @Test
    void shouldAllowServerCopyAsOptionalMode() {
        String command = exporter.defaultCommandTemplate(jobDefinition(Map.of("postgresExportMethod", "server_copy")));

        assertTrue(command.contains("COPY (select"));
        assertTrue(!command.contains("\\\\copy"));
    }

    @Test
    void shouldAllowPsqlCsvModeAsOptionalMode() {
        String command = exporter.defaultCommandTemplate(jobDefinition(Map.of("postgresExportMethod", "psql_csv")));

        assertTrue(command.contains("--csv"));
        assertTrue(command.contains("-P footer=off"));
        assertTrue(command.contains("> '${file}'"));
    }

    @Test
    void shouldAppendCopyPrivilegeGuidance() {
        String message = exporter.buildFailureMessage(
                jobDefinition(Map.of("postgresExportMethod", "server_copy")),
                jobDefinition(Map.of()).tableMappings().get(0),
                "must be superuser or have privileges of the pg_write_server_files role"
        );

        assertTrue(message.contains("pg_write_server_files"));
        assertTrue(message.contains("Prefer psql \\copy"));
    }

    private SyncJobDefinition jobDefinition(Map<String, String> additionalProperties) {
        return new SyncJobDefinition(
                1L,
                "postgres-full-load",
                SyncMode.FULL_ONLY,
                DeploymentArchitecture.AMD64,
                new SourceConnectionProperties(
                        SourceDatabaseType.POSTGRESQL,
                        "127.0.0.1",
                        5432,
                        "inventory",
                        "public",
                        "postgres",
                        "postgres",
                        "jdbc:postgresql://127.0.0.1:5432/inventory",
                        "stringtype=unspecified&sslmode=disable",
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
                        "public",
                        "orders",
                        "target_db",
                        "orders",
                        List.of("id"),
                        "updated_at",
                        List.of("id", "status"),
                        Map.of()
                )),
                new FullLoadConfig(
                        "psql",
                        "./work/export",
                        1000,
                        1,
                        additionalProperties
                ),
                new IncrementalConfig(
                        "sync_server",
                        "sync_slot",
                        "sync_pub",
                        "./work/offsets/offset.dat",
                        5,
                        500,
                        Map.of()
                )
        );
    }
}
