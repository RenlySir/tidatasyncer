package com.example.sync.connectors.export;

import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.SourceDatabaseType;
import org.springframework.stereotype.Component;

@Component
public class PostgreSqlCsvExporter extends AbstractCommandBasedExporter {

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.POSTGRESQL;
    }

    @Override
    protected String defaultCommandTemplate(SyncJobDefinition definition) {
        return "PGPASSWORD='${password}' psql -h ${host} -p ${port} -U ${username} -d ${database} "
                + "-c \"\\\\copy ${schema}.${table} to '${file}' with (format csv, header true)\"";
    }
}
