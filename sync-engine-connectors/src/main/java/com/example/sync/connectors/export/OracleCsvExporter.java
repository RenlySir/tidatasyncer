package com.example.sync.connectors.export;

import com.example.sync.connectors.util.CsvSplitter;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.SourceDatabaseType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OracleCsvExporter extends AbstractCommandBasedExporter {

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.ORACLE;
    }

    @Override
    protected String defaultCommandTemplate(SyncJobDefinition definition) {
        return "${exportToolBinary}"
                + " user=${username}/${password}@//${host}:${port}/${database}"
                + " query=\"select ${oracleQueryHint}${oracleSelectList} from ${oracleTable}${oracleFlashbackClause}\""
                + " head=yes"
                + " text=CSV"
                + " charset=UTF8"
                + " file='${file}'";
    }

    @Override
    protected String defaultExportBinary() {
        return "sqluldr2";
    }

    @Override
    protected List<Path> prepareLightningFiles(
            SyncJobDefinition definition,
            TableMapping mapping,
            Path rawCsvFile,
            Path exportDir
    ) throws IOException {
        return CsvSplitter.splitForLightning(
                rawCsvFile,
                exportDir,
                mapping.targetDatabase(),
                mapping.targetTable(),
                LIGHTNING_CHUNK_SIZE_BYTES
        );
    }

    @Override
    protected void enrichTemplateValues(SyncJobDefinition definition, TableMapping mapping, Map<String, String> values) {
        String flashbackScn = null;
        if (definition.fullLoad().additionalProperties() != null) {
            flashbackScn = trimToNull(firstNonBlank(
                    definition.fullLoad().additionalProperties().get("oracleFlashbackScn"),
                    definition.fullLoad().additionalProperties().get("flashbackScn")
            ));
            String queryHint = trimToNull(firstNonBlank(
                    definition.fullLoad().additionalProperties().get("oracleQueryHint"),
                    definition.fullLoad().additionalProperties().get("oracleParallelHint")
            ));
            values.put("oracleQueryHint", formatOracleHint(queryHint));
        } else {
            values.put("oracleQueryHint", "");
        }
        if (flashbackScn == null && definition.incremental().additionalProperties() != null) {
            flashbackScn = trimToNull(definition.incremental().additionalProperties().get("oracleStartScn"));
        }
        values.put("oracleFlashbackClause", flashbackScn == null || flashbackScn.isBlank() ? "" : " AS OF SCN " + flashbackScn.trim());
    }

    @Override
    protected String buildFailureMessage(SyncJobDefinition definition, TableMapping mapping, String output) {
        if (output != null && output.contains("ORA-01555")) {
            return "Oracle export failed with ORA-01555 snapshot too old while exporting table "
                    + mapping.sourceTable()
                    + ". Consider increasing UNDO_RETENTION or UNDO tablespace size on the source database, exporting with a fixed FLASHBACK SCN, splitting large table exports, and reducing concurrent write activity during export. Original output: "
                    + output;
        }
        return super.buildFailureMessage(definition, mapping, output);
    }

    private String formatOracleHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return "";
        }
        String normalized = hint.trim();
        if (normalized.startsWith("/*+")) {
            return normalized + " ";
        }
        return "/*+ " + normalized + " */ ";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
