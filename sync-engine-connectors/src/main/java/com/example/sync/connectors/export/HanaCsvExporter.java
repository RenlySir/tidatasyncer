package com.example.sync.connectors.export;

import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.SourceDatabaseType;
import org.springframework.stereotype.Component;

@Component
public class HanaCsvExporter extends AbstractCommandBasedExporter {

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.HANA;
    }

    @Override
    protected String defaultCommandTemplate(SyncJobDefinition definition) {
        return "hdbsql -n ${host}:${port} -u ${username} -p '${password}' "
                + "\"select * from \\\"${schema}\\\".\\\"${table}\\\"\" -o ${file}";
    }
}
