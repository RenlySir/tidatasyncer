package com.example.sync.connectors.export;

import com.example.sync.connectors.util.CommandTemplateRenderer;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.spi.CsvExportResult;
import com.example.sync.core.spi.ProgressReporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MySqlCsvExporter extends AbstractCommandBasedExporter {

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.MYSQL;
    }

    @Override
    public CsvExportResult export(SyncJobDefinition definition, ProgressReporter reporter) throws Exception {
        Path exportDir = createExportDir(definition);
        List<TableMapping> mappings = definition.tableMappings();
        List<Path> dumpFiles = new ArrayList<>();
        long exportedBytes = 0L;

        reporter.updateFullLoadMetrics(0, mappings.size(), 0L, "Full export started");

        for (int i = 0; i < mappings.size(); i++) {
            TableMapping mapping = mappings.get(i);
            int percent = Math.max(1, (int) (((i + 1) * 100.0) / mappings.size()));
            reporter.updatePhase(
                    com.example.sync.core.model.JobPhase.EXPORTING_FULL,
                    percent,
                    "Exporting table " + mapping.sourceTable() + " with dumpling"
            );
            executeDumpling(definition, mapping, exportDir);
            List<Path> producedFiles = collectDumplingFiles(definition, mapping, exportDir);
            dumpFiles.addAll(producedFiles);
            exportedBytes += totalBytes(producedFiles);
            reporter.updateFullLoadMetrics(
                    i + 1,
                    mappings.size(),
                    exportedBytes,
                    "Exported " + (i + 1) + "/" + mappings.size() + " tables with dumpling"
            );
        }

        return new CsvExportResult(exportDir, dumpFiles, 0L);
    }

    @Override
    protected String defaultCommandTemplate(SyncJobDefinition definition) {
        return "${exportToolBinary} -h ${host} -P ${port} -u ${username} -p'${password}' "
                + "--consistency snapshot --filetype sql -t ${parallelism} -F 128MiB "
                + "-B ${database} -T ${mysqlTable} -o '${outputDir}'";
    }

    @Override
    protected String defaultExportBinary() {
        return "dumpling";
    }

    private Path createExportDir(SyncJobDefinition definition) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Path baseDir = Path.of(definition.fullLoad().exportBaseDir() == null ? "work/export" : definition.fullLoad().exportBaseDir());
        Path exportDir = baseDir.resolve("job-" + definition.jobId() + "-" + timestamp);
        Files.createDirectories(exportDir);
        return exportDir;
    }

    private void executeDumpling(SyncJobDefinition definition, TableMapping mapping, Path exportDir) throws Exception {
        String template = definition.source().commandTemplate() != null && !definition.source().commandTemplate().isBlank()
                ? definition.source().commandTemplate()
                : defaultCommandTemplate(definition);

        Map<String, String> values = new HashMap<>();
        values.put("host", definition.source().host());
        values.put("port", definition.source().port() == null ? "3306" : String.valueOf(definition.source().port()));
        values.put("database", definition.source().databaseName());
        values.put("schema", mapping.sourceSchema());
        values.put("table", mapping.sourceTable());
        values.put("username", definition.source().username());
        values.put("password", definition.source().password());
        values.put("outputDir", exportDir.toAbsolutePath().toString());
        values.put("parallelism", String.valueOf(definition.fullLoad().parallelism() == null ? 1 : definition.fullLoad().parallelism()));
        values.put("exportToolBinary", resolveExportBinary(definition, defaultExportBinary()));
        values.put("mysqlTable", com.example.sync.connectors.util.SqlIdentifierQuoter.mysqlTable(mapping));

        Process process = new ProcessBuilder("/bin/zsh", "-lc", CommandTemplateRenderer.render(template, values))
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            throw new IllegalStateException("Dumpling export failed: " + output);
        }
    }

    private List<Path> collectDumplingFiles(SyncJobDefinition definition, TableMapping mapping, Path exportDir) throws IOException {
        String sourceDatabase = mapping.sourceCatalog() == null || mapping.sourceCatalog().isBlank()
                ? definition.source().databaseName()
                : mapping.sourceCatalog();
        String sourcePrefix = sourceDatabase + "." + mapping.sourceTable();
        List<Path> producedFiles;
        try (var stream = Files.list(exportDir)) {
            producedFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.startsWith(sourcePrefix)
                                && (fileName.endsWith(".sql") || fileName.endsWith("-schema.sql") || fileName.endsWith("-schema-create.sql"));
                    })
                    .sorted()
                    .toList();
        }
        if (producedFiles.isEmpty()) {
            throw new IllegalStateException("Dumpling did not produce SQL dump files for " + sourcePrefix);
        }
        return producedFiles;
    }
}
