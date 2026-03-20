package com.example.sync.connectors.export;

import com.example.sync.connectors.util.CsvSplitter;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.SourceDatabaseType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OracleCsvExporter extends AbstractCommandBasedExporter {

    static final long LIGHTNING_CHUNK_SIZE_BYTES = 128L * 1024L * 1024L;

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.ORACLE;
    }

    @Override
    protected String defaultCommandTemplate(SyncJobDefinition definition) {
        String binary = resolveExportBinary(definition, "sqluldr2");
        return binary
                + " user=${username}/${password}@//${host}:${port}/${database}"
                + " query=\"select * from ${schema}.${table}\""
                + " head=yes"
                + " text=CSV"
                + " charset=UTF8"
                + " file=${file}";
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
}
