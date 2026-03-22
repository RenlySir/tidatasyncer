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

class SqlServerCsvExporterTest {

    private final SqlServerCsvExporter exporter = new SqlServerCsvExporter();

    @Test
    void shouldUseBcpByDefault() {
        SyncJobDefinition definition = jobDefinition(Map.of("sqlServerExportTool", "bcp"));

        String command = exporter.defaultCommandTemplate(definition);

        assertTrue(command.contains("queryout '${file}'"));
    }

    @Test
    void shouldAllowSqlcmdAsOptionalExporter() {
        SyncJobDefinition definition = jobDefinition(Map.of("sqlServerExportTool", "sqlcmd"));

        String command = exporter.defaultCommandTemplate(definition);

        assertTrue(command.contains("-Q \"SET NOCOUNT ON; SELECT ${sqlServerSelectList} FROM ${sqlServerTable}\""));
        assertTrue(command.contains("-s,"));
        assertTrue(command.contains("-y 0"));
    }

    private SyncJobDefinition jobDefinition(Map<String, String> additionalProperties) {
        return new SyncJobDefinition(
                1L,
                "sqlserver-full-load",
                SyncMode.FULL_ONLY,
                DeploymentArchitecture.AMD64,
                new SourceConnectionProperties(
                        SourceDatabaseType.SQLSERVER,
                        "127.0.0.1",
                        1433,
                        "source_db",
                        "dbo",
                        "sa",
                        "password",
                        "jdbc:sqlserver://127.0.0.1:1433;databaseName=source_db",
                        "encrypt=false;trustServerCertificate=true",
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
                        "dbo",
                        "orders",
                        "target_db",
                        "orders",
                        List.of("id"),
                        "updated_at",
                        List.of("id", "status"),
                        Map.of()
                )),
                new FullLoadConfig(
                        null,
                        "./work/export",
                        1000,
                        2,
                        additionalProperties
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
