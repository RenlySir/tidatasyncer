package com.example.sync.connectors.importer;

import com.example.sync.connectors.util.ProjectManagedToolResolver;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.spi.CsvExportResult;
import com.example.sync.core.spi.FullLoadImporter;
import com.example.sync.core.spi.ProgressReporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TiDbLightningImporter implements FullLoadImporter {

    @Override
    public void importCsv(SyncJobDefinition definition, CsvExportResult exportResult, ProgressReporter reporter) throws Exception {
        reporter.updatePhase(com.example.sync.core.model.JobPhase.IMPORTING_FULL, 5, "Generating TiDB Lightning config");
        Path configFile = Files.createTempFile("tidb-lightning-", ".toml");
        Files.writeString(configFile, buildConfig(definition, exportResult));
        int totalTables = resolveTableCount(definition, exportResult);
        long importBytes = totalBytes(exportResult);
        reporter.updateImportMetrics(0, totalTables, 0L, "TiDB Lightning import prepared");

        String binary = ProjectManagedToolResolver.resolveTidbLightningBinary(
                definition.target().lightningBinary(),
                definition.deploymentArchitecture()
        );
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
        reporter.updateImportMetrics(totalTables, totalTables, importBytes, "TiDB Lightning import finished");
        reporter.updatePhase(com.example.sync.core.model.JobPhase.IMPORTING_FULL, 100, "TiDB Lightning import finished");
    }

    String buildConfig(SyncJobDefinition definition, CsvExportResult exportResult) {
        boolean csvMode = usesCsvMode(exportResult);
        StringBuilder builder = new StringBuilder();
        builder.append("""
                [lightning]
                level = "info"
                
                [tikv-importer]
                backend = "local"
                
                [mydumper]
                data-source-dir = "%s"
                data-character-set = "utf8mb4"
                strict-format = false
                """.formatted(
                exportResult.exportDirectory().toAbsolutePath()
        ));
        if (csvMode) {
            builder.append("""
                    csv.separator = ","
                    csv.delimiter = "\""
                    csv.header = true

                    """);
        }
        builder.append(buildMySqlSqlFileRules(definition, exportResult));
        builder.append("""
                [tidb]
                host = "%s"
                port = %d
                user = "%s"
                password = "%s"
                status-port = %d
                pd-addr = "%s:2379"
                """.formatted(
                definition.target().host(),
                definition.target().port(),
                definition.target().username(),
                definition.target().password(),
                definition.target().statusPort() == null ? 10080 : definition.target().statusPort(),
                definition.target().host()
        ));
        return builder.toString();
    }

    private boolean usesCsvMode(CsvExportResult exportResult) {
        return exportResult.csvFiles().stream().anyMatch(path -> path.getFileName().toString().endsWith(".csv"));
    }

    private String buildMySqlSqlFileRules(SyncJobDefinition definition, CsvExportResult exportResult) {
        boolean hasSqlDump = exportResult.csvFiles().stream().anyMatch(path -> path.getFileName().toString().endsWith(".sql"));
        if (definition.source().databaseType() != SourceDatabaseType.MYSQL || !hasSqlDump) {
            return "";
        }

        Map<String, String> databaseRoutes = new LinkedHashMap<>();
        for (TableMapping mapping : definition.tableMappings()) {
            String sourceDatabase = firstNonBlank(mapping.sourceCatalog(), definition.source().databaseName(), mapping.sourceSchema());
            String previous = databaseRoutes.putIfAbsent(sourceDatabase, mapping.targetDatabase());
            if (previous != null && !previous.equals(mapping.targetDatabase())) {
                throw new IllegalArgumentException(
                        "MySQL dumpling SQL import currently requires a single target database per source database. "
                                + "Found source database " + sourceDatabase + " mapped to both " + previous + " and " + mapping.targetDatabase()
                );
            }
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> route : databaseRoutes.entrySet()) {
            builder.append("""

                    [[mydumper.files]]
                    pattern = '^%s-schema-create\\.sql$'
                    schema = '%s'
                    type = 'schema-schema'
                    """.formatted(regex(route.getKey()), route.getValue()));
        }

        for (TableMapping mapping : definition.tableMappings()) {
            String sourceDatabase = firstNonBlank(mapping.sourceCatalog(), definition.source().databaseName(), mapping.sourceSchema());
            builder.append("""

                    [[mydumper.files]]
                    pattern = '^%s\\.%s-schema\\.sql$'
                    schema = '%s'
                    table = '%s'
                    type = 'table-schema'
                    """.formatted(
                    regex(sourceDatabase),
                    regex(mapping.sourceTable()),
                    mapping.targetDatabase(),
                    mapping.targetTable()
            ));
            builder.append("""

                    [[mydumper.files]]
                    pattern = '^%s\\.%s(?:\\.[0-9]+)?\\.sql$'
                    schema = '%s'
                    table = '%s'
                    type = 'sql'
                    """.formatted(
                    regex(sourceDatabase),
                    regex(mapping.sourceTable()),
                    mapping.targetDatabase(),
                    mapping.targetTable()
            ));
        }
        return builder.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String regex(String literal) {
        StringBuilder builder = new StringBuilder(literal.length() * 2);
        for (char ch : literal.toCharArray()) {
            if ("\\.[]{}()+-*?^$|".indexOf(ch) >= 0) {
                builder.append('\\');
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    private int resolveTableCount(SyncJobDefinition definition, CsvExportResult exportResult) {
        if (definition.tableMappings() != null && !definition.tableMappings().isEmpty()) {
            return definition.tableMappings().size();
        }
        Set<String> tables = new LinkedHashSet<>();
        for (Path file : exportResult.csvFiles()) {
            String fileName = file.getFileName().toString();
            String[] segments = fileName.split("\\.");
            if (segments.length >= 2) {
                tables.add(segments[0] + "." + segments[1]);
            }
        }
        return tables.size();
    }

    private long totalBytes(CsvExportResult exportResult) throws java.io.IOException {
        long total = 0L;
        for (Path file : exportResult.csvFiles()) {
            total += Files.size(file);
        }
        return total;
    }
}
