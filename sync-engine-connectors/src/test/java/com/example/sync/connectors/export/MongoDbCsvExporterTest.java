package com.example.sync.connectors.export;

import static org.junit.jupiter.api.Assertions.assertThrows;
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

class MongoDbCsvExporterTest {

    @Test
    void shouldUseMongoExportCommandTemplate() {
        MongoDbCsvExporter exporter = new MongoDbCsvExporter();

        String command = exporter.defaultCommandTemplate(jobDefinition(List.of("_id", "name")));

        assertTrue(command.startsWith("${exportToolBinary}"));
        assertTrue(command.contains("--uri='${connectionUri}'"));
        assertTrue(command.contains("--fieldFile='${fieldFile}'"));
        assertTrue(command.contains("--type=csv"));
    }

    @Test
    void shouldRequireIncludedColumnsForMongoCsvExport() {
        MongoDbCsvExporter exporter = new MongoDbCsvExporter();
        TableMapping mapping = jobDefinition(List.of()).tableMappings().get(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> exporter.validateMapping(jobDefinition(List.of()), mapping)
        );
    }

    private SyncJobDefinition jobDefinition(List<String> includedColumns) {
        return new SyncJobDefinition(
                1L,
                "mongodb-to-tidb",
                SyncMode.FULL_AND_INCREMENTAL,
                DeploymentArchitecture.AMD64,
                new SourceConnectionProperties(
                        SourceDatabaseType.MONGODB,
                        "127.0.0.1",
                        27017,
                        "source_db",
                        null,
                        "mongo",
                        "mongo",
                        "mongodb://mongo:mongo@127.0.0.1:27017/source_db?authSource=admin",
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
                        "users",
                        "target_db",
                        "users",
                        List.of("_id"),
                        "",
                        includedColumns,
                        Map.of("_id", "id", "address.city", "city")
                )),
                new FullLoadConfig(
                        null,
                        "./work/export",
                        1000,
                        1,
                        Map.of()
                ),
                new IncrementalConfig(
                        "sync_server",
                        "",
                        "",
                        "./work/offsets/offset.dat",
                        5,
                        500,
                        Map.of("authSource", "admin")
                )
        );
    }
}
