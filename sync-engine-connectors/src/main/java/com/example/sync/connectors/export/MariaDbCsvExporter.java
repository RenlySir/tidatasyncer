package com.example.sync.connectors.export;

import com.example.sync.connectors.util.CommandTemplateRenderer;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.model.SourceDatabaseType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MariaDbCsvExporter extends AbstractCommandBasedExporter {

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.MARIADB;
    }

    @Override
    protected String defaultCommandTemplate(SyncJobDefinition definition) {
        return "${exportToolBinary} --tab='${outputDir}' "
                + "--fields-terminated-by=, --fields-optionally-enclosed-by='\"' --lines-terminated-by='\\n' "
                + "--no-create-info --skip-comments --quick "
                + "-h ${host} -P ${port} -u ${username} -p'${password}' ${database} ${table}";
    }

    @Override
    protected String defaultExportBinary() {
        return "mariadb-dump";
    }

    @Override
    protected boolean exportProducesHeader(SyncJobDefinition definition) {
        return false;
    }

    @Override
    protected void executeExportCommand(SyncJobDefinition definition, TableMapping mapping, Path csvFile) throws Exception {
        Path exportDir = csvFile.getParent().resolve(".mariadb-" + mapping.targetTable());
        Files.createDirectories(exportDir);

        String template = definition.source().commandTemplate() != null && !definition.source().commandTemplate().isBlank()
                ? definition.source().commandTemplate()
                : defaultCommandTemplate(definition);

        Map<String, String> values = new HashMap<>();
        values.put("host", definition.source().host());
        values.put("port", definition.source().port() == null ? "3306" : String.valueOf(definition.source().port()));
        values.put("database", definition.source().databaseName());
        values.put("table", mapping.sourceTable());
        values.put("username", definition.source().username());
        values.put("password", definition.source().password());
        values.put("outputDir", exportDir.toAbsolutePath().toString());
        values.put("exportToolBinary", resolveExportBinary(definition, defaultExportBinary()));

        Process process = new ProcessBuilder("/bin/zsh", "-lc", CommandTemplateRenderer.render(template, values))
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            throw new IllegalStateException("MariaDB export command failed: " + output);
        }

        Path exportedFile = exportDir.resolve(mapping.sourceTable() + ".txt");
        if (!Files.exists(exportedFile)) {
            throw new IllegalStateException("MariaDB export did not produce text file for table " + mapping.sourceTable());
        }
        Files.move(exportedFile, csvFile, StandardCopyOption.REPLACE_EXISTING);
    }
}
