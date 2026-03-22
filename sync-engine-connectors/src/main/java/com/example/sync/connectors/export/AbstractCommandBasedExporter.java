package com.example.sync.connectors.export;

import com.example.sync.connectors.util.CommandTemplateRenderer;
import com.example.sync.connectors.util.CsvHeaderMapper;
import com.example.sync.connectors.util.CsvSplitter;
import com.example.sync.connectors.util.JdbcConnectionSupport;
import com.example.sync.connectors.util.MongoConnectionSupport;
import com.example.sync.connectors.util.ProjectManagedToolResolver;
import com.example.sync.connectors.util.SqlIdentifierQuoter;
import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.spi.CsvExportResult;
import com.example.sync.core.spi.FullLoadExporter;
import com.example.sync.core.spi.ProgressReporter;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractCommandBasedExporter implements FullLoadExporter {

    protected static final long LIGHTNING_CHUNK_SIZE_BYTES = 128L * 1024L * 1024L;

    @Override
    public CsvExportResult export(SyncJobDefinition definition, ProgressReporter reporter) throws Exception {
        Path exportDir = createExportDir(definition);
        List<Path> csvFiles = new ArrayList<>();
        long rowCount = 0L;
        long exportedBytes = 0L;
        List<TableMapping> mappings = definition.tableMappings();

        reporter.updateFullLoadMetrics(0, mappings.size(), 0L, "Full export started");

        for (int i = 0; i < mappings.size(); i++) {
            TableMapping mapping = mappings.get(i);
            int percent = Math.max(1, (int) (((i + 1) * 100.0) / mappings.size()));
            reporter.updatePhase(
                    com.example.sync.core.model.JobPhase.EXPORTING_FULL,
                    percent,
                    "Exporting table " + mapping.sourceTable()
            );
            validateMapping(definition, mapping);
            Path rawCsvFile = rawExportFile(exportDir, mapping);
            executeExportCommand(definition, mapping, rawCsvFile);
            ensureHeaderRow(definition, mapping, rawCsvFile);
            rowCount += countRows(rawCsvFile);
            List<Path> preparedFiles = prepareLightningFiles(definition, mapping, rawCsvFile, exportDir);
            csvFiles.addAll(preparedFiles);
            exportedBytes += totalBytes(preparedFiles);
            reporter.updateFullLoadMetrics(
                    i + 1,
                    mappings.size(),
                    exportedBytes,
                    "Exported " + (i + 1) + "/" + mappings.size() + " tables"
            );
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
        values.put("parallelism", String.valueOf(definition.fullLoad().parallelism() == null ? 1 : definition.fullLoad().parallelism()));
        values.put("fetchSize", String.valueOf(definition.fullLoad().fetchSize() == null ? 1000 : definition.fullLoad().fetchSize()));
        values.put("jdbcUrl", JdbcConnectionSupport.previewSourceUrl(source));
        values.put("connectionUri", MongoConnectionSupport.resolveConnectionString(source, definition.incremental().additionalProperties()));
        values.put("exportToolBinary", resolveExportBinary(definition, defaultExportBinary()));
        values.put("file", csvFile.toAbsolutePath().toString());
        values.put("outputDir", csvFile.getParent().toAbsolutePath().toString());
        values.put("fields", String.join(",", mapping.includedColumns() == null ? List.of() : mapping.includedColumns()));
        values.put("fieldFile", createFieldFile(csvFile.getParent(), mapping).toAbsolutePath().toString());
        values.put("mysqlTable", SqlIdentifierQuoter.mysqlTable(mapping));
        values.put("mysqlSelectList", SqlIdentifierQuoter.mysqlSelectList(mapping));
        values.put("postgresqlTable", SqlIdentifierQuoter.postgresqlTable(mapping));
        values.put("postgresqlSelectList", SqlIdentifierQuoter.postgresqlSelectList(mapping));
        values.put("db2Table", SqlIdentifierQuoter.db2Table(mapping));
        values.put("db2SelectList", SqlIdentifierQuoter.db2SelectList(mapping));
        values.put("sqlServerTable", SqlIdentifierQuoter.sqlServerTable(mapping));
        values.put("sqlServerSelectList", SqlIdentifierQuoter.sqlServerSelectList(mapping));
        values.put("hanaTable", SqlIdentifierQuoter.hanaTable(mapping));
        values.put("hanaSelectList", SqlIdentifierQuoter.hanaSelectList(mapping));
        values.put("oracleTable", SqlIdentifierQuoter.oracleTable(mapping));
        values.put("oracleSelectList", SqlIdentifierQuoter.oracleSelectList(mapping));
        if (definition.fullLoad().additionalProperties() != null) {
            definition.fullLoad().additionalProperties().forEach((key, value) -> values.put(key, value == null ? "" : value));
        }
        enrichTemplateValues(definition, mapping, values);

        String renderedCommand = CommandTemplateRenderer.render(template, values);
        Process process = new ProcessBuilder("/bin/zsh", "-lc", renderedCommand)
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException(buildFailureMessage(definition, mapping, output));
        }
    }

    protected void enrichTemplateValues(SyncJobDefinition definition, TableMapping mapping, Map<String, String> values) {
    }

    protected String buildFailureMessage(SyncJobDefinition definition, TableMapping mapping, String output) {
        return "Export command failed: " + output;
    }

    protected boolean exportProducesHeader(SyncJobDefinition definition) {
        return true;
    }

    protected void ensureHeaderRow(SyncJobDefinition definition, TableMapping mapping, Path rawCsvFile) throws Exception {
        if (exportProducesHeader(definition)) {
            return;
        }
        List<String> headerColumns = resolveHeaderColumns(definition, mapping);
        if (headerColumns.isEmpty()) {
            return;
        }

        Path tempFile = Files.createTempFile(rawCsvFile.getParent(), ".header-", ".csv");
        try (Writer writer = new OutputStreamWriter(new BufferedOutputStream(Files.newOutputStream(tempFile)), StandardCharsets.UTF_8)) {
            writer.write(String.join(",", headerColumns));
            writer.write('\n');
            writer.flush();
        }
        try (var outputStream = new BufferedOutputStream(Files.newOutputStream(tempFile, java.nio.file.StandardOpenOption.APPEND))) {
            Files.copy(rawCsvFile, outputStream);
        }
        Files.move(tempFile, rawCsvFile, StandardCopyOption.REPLACE_EXISTING);
    }

    protected List<String> resolveHeaderColumns(SyncJobDefinition definition, TableMapping mapping) throws Exception {
        if (mapping.includedColumns() != null && !mapping.includedColumns().isEmpty()) {
            return mapping.includedColumns();
        }

        try (Connection connection = DriverManager.getConnection(
                JdbcConnectionSupport.resolveSourceJdbcUrl(definition.source()),
                definition.source().username(),
                definition.source().password()
        )) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(
                    resolveColumnCatalog(definition, mapping),
                    resolveColumnSchema(definition, mapping),
                    mapping.sourceTable(),
                    "%"
            )) {
                List<ColumnMetadata> results = new ArrayList<>();
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    int ordinal = columns.getInt("ORDINAL_POSITION");
                    if (columnName != null && !columnName.isBlank()) {
                        results.add(new ColumnMetadata(ordinal, columnName));
                    }
                }
                results.sort(Comparator.comparingInt(ColumnMetadata::ordinalPosition));
                return results.stream().map(ColumnMetadata::columnName).toList();
            }
        }
    }

    private String resolveColumnCatalog(SyncJobDefinition definition, TableMapping mapping) {
        return switch (definition.source().databaseType()) {
            case MYSQL, MARIADB, SQLSERVER -> firstNonBlank(mapping.sourceCatalog(), definition.source().databaseName());
            default -> null;
        };
    }

    private String resolveColumnSchema(SyncJobDefinition definition, TableMapping mapping) {
        return switch (definition.source().databaseType()) {
            case ORACLE, DB2, HANA -> firstNonBlank(mapping.sourceSchema(), definition.source().schemaName(), definition.source().username()).toUpperCase();
            case POSTGRESQL -> firstNonBlank(mapping.sourceSchema(), definition.source().schemaName(), "public");
            case SQLSERVER -> firstNonBlank(mapping.sourceSchema(), definition.source().schemaName(), "dbo");
            case MYSQL, MARIADB -> firstNonBlank(mapping.sourceSchema(), definition.source().schemaName(), definition.source().databaseName());
            default -> null;
        };
    }

    protected String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
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
        CsvHeaderMapper.rewriteHeader(rawCsvFile, mapping.columnMappings());
        return CsvSplitter.splitForLightning(
                rawCsvFile,
                exportDir,
                mapping.targetDatabase(),
                mapping.targetTable(),
                LIGHTNING_CHUNK_SIZE_BYTES
        );
    }

    protected void validateMapping(SyncJobDefinition definition, TableMapping mapping) {
    }

    protected String defaultExportBinary() {
        return "";
    }

    protected String resolveExportBinary(SyncJobDefinition definition, String defaultBinary) {
        String configured = definition.fullLoad() == null ? null : definition.fullLoad().exportToolBinary();
        if (configured == null || configured.isBlank()) {
            return resolveManagedBinary(definition, defaultBinary);
        }
        return configured;
    }

    private String resolveManagedBinary(SyncJobDefinition definition, String defaultBinary) {
        try {
            if ("sqluldr2".equals(defaultBinary)) {
                return ProjectManagedToolResolver.resolveSqluldr2Binary(null, definition.deploymentArchitecture());
            }
            if ("dumpling".equals(defaultBinary)) {
                return ProjectManagedToolResolver.resolveDumplingBinary(null, definition.deploymentArchitecture());
            }
            if ("bcp".equals(defaultBinary)) {
                return ProjectManagedToolResolver.resolveBcpBinary(null, definition.deploymentArchitecture());
            }
            if ("sqlcmd".equals(defaultBinary)) {
                return ProjectManagedToolResolver.resolveSqlcmdBinary(null, definition.deploymentArchitecture());
            }
            return defaultBinary;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve managed binary for " + defaultBinary + ": " + ex.getMessage(), ex);
        }
    }

    protected Path createFieldFile(Path exportDir, TableMapping mapping) throws IOException {
        Path fieldFile = exportDir.resolve(".fields-" + mapping.targetTable() + ".txt");
        List<String> fields = mapping.includedColumns() == null ? List.of() : mapping.includedColumns();
        Files.write(fieldFile, fields);
        return fieldFile;
    }

    protected abstract String defaultCommandTemplate(SyncJobDefinition definition);

    protected long totalBytes(List<Path> files) throws IOException {
        long total = 0L;
        for (Path file : files) {
            total += Files.size(file);
        }
        return total;
    }

    private record ColumnMetadata(int ordinalPosition, String columnName) {
    }
}
