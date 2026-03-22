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
        return "${exportToolBinary} -n ${host}:${port} -u ${username} -p '${password}' "
                + "\"select ${hanaSelectList} from ${hanaTable}\" -o '${file}'";
    }

    @Override
    protected String defaultExportBinary() {
        return "hdbsql";
    }

    @Override
    protected boolean exportProducesHeader(SyncJobDefinition definition) {
        return false;
    }
}
