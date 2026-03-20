package com.example.sync.connectors.export;

import com.example.sync.connectors.util.CommandTemplateRenderer;
import com.example.sync.connectors.util.LightningCsvNaming;
import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.spi.CsvExportResult;
import com.example.sync.core.spi.FullLoadExporter;
import com.example.sync.core.spi.ProgressReporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractCommandBasedExporter implements FullLoadExporter {

    @Override
    public CsvExportResult export(SyncJobDefinition definition, ProgressReporter reporter) throws Exception {
        Path exportDir = createExportDir(definition);
        List<Path> csvFiles = new ArrayList<>();
        long rowCount = 0L;
        List<TableMapping> mappings = definition.tableMappings();

        for (int i = 0; i < mappings.size(); i++) {
            TableMapping mapping = mappings.get(i);
            int percent = Math.max(1, (int) (((i + 1) * 100.0) / mappings.size()));
            reporter.updatePhase(
                    com.example.sync.core.model.JobPhase.EXPORTING_FULL,
                    percent,
                    "Exporting table " + mapping.sourceTable()
            );
            Path rawCsvFile = rawExportFile(exportDir, mapping);
            executeExportCommand(definition, mapping, rawCsvFile);
            rowCount += countRows(rawCsvFile);
            csvFiles.addAll(prepareLightningFiles(definition, mapping, rawCsvFile, exportDir));
        }

        return new CsvExportResult(exportDir, csvFiles, rowCount);
    }

    private Path createExportDir(SyncJobDefinition definition) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Path baseDir = Path.of(definition.fullLoad().exportBaseDir() == null ? "work/export" : definition.fullLoad().exportBaseDir());
        Path exportDir = baseDir.resolve("job-" + definition.jobId() + "-" + timestamp);
        Files.createDirectories(exportDir);
        return exportDir;
    }

    protected void executeExportCommand(SyncJobDefinition definition, TableMapping mapping, Path csvFile) throws Exception {
        Files.createDirectories(csvFile.getParent());
        SourceConnectionProperties source = definition.source();
        String template = source.commandTemplate() != null && !source.commandTemplate().isBlank()
                ? source.commandTemplate()
                : defaultCommandTemplate(definition);

        Map<String, String> values = new HashMap<>();
        values.put("host", source.host());
        values.put("port", source.port() == null ? "" : String.valueOf(source.port()));
        values.put("database", source.databaseName());
        values.put("schema", mapping.sourceSchema());
        values.put("table", mapping.sourceTable());
        values.put("username", source.username());
        values.put("password", source.password());
        values.put("jdbcUrl", source.jdbcUrl());
        values.put("exportToolBinary", resolveExportBinary(definition, ""));
        values.put("file", csvFile.toAbsolutePath().toString());

        Process process = new ProcessBuilder("/bin/zsh", "-lc", CommandTemplateRenderer.render(template, values))
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            throw new IllegalStateException("Export command failed: " + output);
        }
    }

    protected long countRows(Path csvFile) throws IOException {
        try (var lines = Files.lines(csvFile)) {
            return Math.max(0L, lines.count() - 1L);
        }
    }

    protected Path rawExportFile(Path exportDir, TableMapping mapping) {
        return exportDir.resolve(".raw-" + mapping.targetDatabase() + "." + mapping.targetTable() + ".csv");
    }

    protected List<Path> prepareLightningFiles(
            SyncJobDefinition definition,
            TableMapping mapping,
            Path rawCsvFile,
            Path exportDir
    ) throws IOException {
        Path targetFile = exportDir.resolve(LightningCsvNaming.singleFileName(mapping.targetDatabase(), mapping.targetTable()));
        Files.move(rawCsvFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        return List.of(targetFile);
    }

    protected String resolveExportBinary(SyncJobDefinition definition, String defaultBinary) {
        String configured = definition.fullLoad() == null ? null : definition.fullLoad().exportToolBinary();
        if (configured == null || configured.isBlank()) {
            return defaultBinary;
        }
        return configured;
    }

    protected abstract String defaultCommandTemplate(SyncJobDefinition definition);
}
