package com.example.sync.connectors.importer;

import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.spi.CsvExportResult;
import com.example.sync.core.spi.FullLoadImporter;
import com.example.sync.core.spi.ProgressReporter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class TiDbLightningImporter implements FullLoadImporter {

    @Override
    public void importCsv(SyncJobDefinition definition, CsvExportResult exportResult, ProgressReporter reporter) throws Exception {
        reporter.updatePhase(com.example.sync.core.model.JobPhase.IMPORTING_FULL, 5, "Generating TiDB Lightning config");
        Path configFile = Files.createTempFile("tidb-lightning-", ".toml");
        Files.writeString(configFile, buildConfig(definition, exportResult.exportDirectory()));

        String binary = definition.target().lightningBinary() == null || definition.target().lightningBinary().isBlank()
                ? "tidb-lightning"
                : definition.target().lightningBinary();
        String command = binary + " --config " + configFile.toAbsolutePath();

        reporter.updatePhase(com.example.sync.core.model.JobPhase.IMPORTING_FULL, 20, "Running TiDB Lightning import");
        Process process = new ProcessBuilder("/bin/zsh", "-lc", command)
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            throw new IllegalStateException("TiDB Lightning import failed: " + output);
        }
        reporter.updatePhase(com.example.sync.core.model.JobPhase.IMPORTING_FULL, 100, "TiDB Lightning import finished");
    }

    private String buildConfig(SyncJobDefinition definition, Path exportDir) {
        return """
                [lightning]
                level = "info"
                
                [tikv-importer]
                backend = "local"
                
                [mydumper]
                data-source-dir = "%s"
                data-character-set = "utf8mb4"
                strict-format = false
                csv.separator = ","
                csv.delimiter = "\""
                csv.header = true
                
                [tidb]
                host = "%s"
                port = %d
                user = "%s"
                password = "%s"
                status-port = 10080
                pd-addr = "%s:2379"
                """.formatted(
                exportDir.toAbsolutePath(),
                definition.target().host(),
                definition.target().port(),
                definition.target().username(),
                definition.target().password(),
                definition.target().host()
        );
    }
}
