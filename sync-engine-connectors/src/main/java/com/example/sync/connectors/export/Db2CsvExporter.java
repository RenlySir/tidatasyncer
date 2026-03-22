package com.example.sync.connectors.export;

import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.SourceDatabaseType;
import org.springframework.stereotype.Component;

@Component
public class Db2CsvExporter extends AbstractCommandBasedExporter {

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.DB2;
    }

    @Override
    protected String defaultCommandTemplate(SyncJobDefinition definition) {
        return "DB2CODEPAGE=1208 ${exportToolBinary} \"CONNECT TO ${database} USER ${username} USING ${password}\" "
                + "&& DB2CODEPAGE=1208 ${exportToolBinary} \"EXPORT TO ${file} OF DEL SELECT ${db2SelectList} FROM ${db2Table}\" "
                + "&& ${exportToolBinary} \"CONNECT RESET\"";
    }

    @Override
    protected String defaultExportBinary() {
        return "db2";
    }

    @Override
    protected boolean exportProducesHeader(SyncJobDefinition definition) {
        return false;
    }
}
