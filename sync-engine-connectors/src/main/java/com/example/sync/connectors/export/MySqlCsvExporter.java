package com.example.sync.connectors.export;

import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.SourceDatabaseType;
import org.springframework.stereotype.Component;

@Component
public class MySqlCsvExporter extends AbstractCommandBasedExporter {

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.MYSQL;
    }

    @Override
    protected String defaultCommandTemplate(SyncJobDefinition definition) {
        return "mysqlsh --uri ${username}:${password}@${host}:${port}/${database} "
                + "-- util exportTable ${database}.${table} --outputUrl=${file} --dialect=csv";
    }
}
