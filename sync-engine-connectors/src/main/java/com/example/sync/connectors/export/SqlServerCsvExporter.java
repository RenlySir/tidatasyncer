package com.example.sync.connectors.export;

import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.SourceDatabaseType;
import org.springframework.stereotype.Component;

@Component
public class SqlServerCsvExporter extends AbstractCommandBasedExporter {

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.SQLSERVER;
    }

    @Override
    protected String defaultCommandTemplate(SyncJobDefinition definition) {
        return "bcp \"select * from ${schema}.${table}\" queryout ${file} -c -t, -r\\\\n "
                + "-S ${host},${port} -U ${username} -P '${password}' -d ${database}";
    }
}
