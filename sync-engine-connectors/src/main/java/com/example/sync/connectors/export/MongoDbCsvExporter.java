package com.example.sync.connectors.export;

import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.model.SourceDatabaseType;
import org.springframework.stereotype.Component;

@Component
public class MongoDbCsvExporter extends AbstractCommandBasedExporter {

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.MONGODB;
    }

    @Override
    protected void validateMapping(SyncJobDefinition definition, TableMapping mapping) {
        if (mapping.includedColumns() == null || mapping.includedColumns().isEmpty()) {
            throw new IllegalArgumentException("MongoDB full export requires includedColumns for collection " + mapping.sourceTable());
        }
    }

    @Override
    protected String defaultCommandTemplate(SyncJobDefinition definition) {
        String binary = resolveExportBinary(definition, "mongoexport");
        return binary
                + " --uri='${connectionUri}'"
                + " --db='${database}'"
                + " --collection='${table}'"
                + " --type=csv"
                + " --fieldFile='${fieldFile}'"
                + " --out='${file}'";
    }
}
