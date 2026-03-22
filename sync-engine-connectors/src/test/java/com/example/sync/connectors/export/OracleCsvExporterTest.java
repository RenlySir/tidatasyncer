package com.example.sync.connectors.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.sync.core.config.FullLoadConfig;
import com.example.sync.core.config.IncrementalConfig;
import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.config.TargetConnectionProperties;
import com.example.sync.core.model.DeploymentArchitecture;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.model.SyncMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OracleCsvExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPrepareLightningCompatibleFilesWith128MiBStrategy() throws Exception {
        OracleCsvExporter exporter = new OracleCsvExporter();
        TableMapping mapping = new TableMapping(
                "source_db",
                "APP",
                "ORDERS",
                "target_db",
                "orders",
                List.of("ID"),
                "UPDATED_AT",
                List.of("ID", "UPDATED_AT"),
                Map.of()
        );
        Path rawFile = tempDir.resolve(".raw-target_db.orders.csv");
        Files.writeString(rawFile, "ID,UPDATED_AT\n1,2026-03-20 12:00:00\n", StandardCharsets.UTF_8);

        List<Path> files = exporter.prepareLightningFiles(jobDefinition(), mapping, rawFile, tempDir);

        assertEquals(1, files.size());
        assertEquals("target_db.orders.00000001.csv", files.get(0).getFileName().toString());
        assertTrue(Files.exists(files.get(0)));
    }

    @Test
    void shouldDefaultToSqluldrBinaryWhenNoOverrideProvided() {
        OracleCsvExporter exporter = new OracleCsvExporter();

        String command = exporter.defaultCommandTemplate(jobDefinition());

        assertTrue(command.startsWith("${exportToolBinary}"));
        assertTrue(command.contains("text=CSV"));
        assertTrue(command.contains("head=yes"));
    }

    @Test
    void shouldInjectFlashbackScnIntoOracleQuery() throws Exception {
        TestOracleCsvExporter exporter = new TestOracleCsvExporter();

        exporter.captureTemplateValues(jobDefinitionWithOracleScn("123456"), jobDefinition().tableMappings().get(0), tempDir.resolve("sample.csv"));

        assertEquals(" AS OF SCN 123456", exporter.oracleFlashbackClause);
    }

    @Test
    void shouldAppendSnapshotTooOldGuidance() {
        OracleCsvExporter exporter = new OracleCsvExporter();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            throw new IllegalStateException(exporter.buildFailureMessage(
                    jobDefinition(),
                    jobDefinition().tableMappings().get(0),
                    "ORA-01555 snapshot too old"
            ));
        });

        assertTrue(ex.getMessage().contains("ORA-01555"));
        assertTrue(ex.getMessage().contains("UNDO_RETENTION"));
    }

    private SyncJobDefinition jobDefinition() {
        return jobDefinitionWithOracleScn(null);
    }

    private SyncJobDefinition jobDefinitionWithOracleScn(String scn) {
        return new SyncJobDefinition(
                1L,
                "oracle-to-tidb",
                SyncMode.FULL_ONLY,
                DeploymentArchitecture.AMD64,
                new SourceConnectionProperties(
                        SourceDatabaseType.ORACLE,
                        "127.0.0.1",
                        1521,
                        "orclpdb",
                        "APP",
                        "system",
                        "oracle",
                        "jdbc:oracle:thin:@//127.0.0.1:1521/orclpdb",
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
                        null,
                        tempDir.toString(),
                        1000,
                        1,
                        Map.of()
                ),
                new IncrementalConfig(
                        "sync_server",
                        "sync_slot",
                        "sync_pub",
                        "./work/offsets/offset.dat",
                        5,
                        500,
                        scn == null ? Map.of() : Map.of("oracleStartScn", scn)
                )
        );
    }

    private static final class TestOracleCsvExporter extends OracleCsvExporter {
        private String oracleFlashbackClause;

        private void captureTemplateValues(SyncJobDefinition definition, TableMapping mapping, Path csvFile) throws Exception {
            java.util.Map<String, String> values = new java.util.HashMap<>();
            enrichTemplateValues(definition, mapping, values);
            oracleFlashbackClause = values.get("oracleFlashbackClause");
        }
    }
}
