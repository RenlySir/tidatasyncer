package com.example.sync.connectors.export;

import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.SourceDatabaseType;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PostgreSqlCsvExporter extends AbstractCommandBasedExporter {

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.POSTGRESQL;
    }

    @Override
    protected String defaultCommandTemplate(SyncJobDefinition definition) {
        return switch (exportMethod(definition)) {
            case "server_copy" -> "PGPASSWORD='${password}' PGOPTIONS='-c statement_timeout=0' ${exportToolBinary} "
                    + "-v ON_ERROR_STOP=1 -h ${host} -p ${port} -U ${username} -d ${database} "
                    + "-c \"COPY (select ${postgresqlSelectList} from ${postgresqlTable}) "
                    + "to '${file}' with (format csv, header true, encoding 'UTF8')\"";
            case "psql_csv" -> "PGPASSWORD='${password}' PGOPTIONS='-c statement_timeout=0' ${exportToolBinary} "
                    + "-v ON_ERROR_STOP=1 -P footer=off -h ${host} -p ${port} -U ${username} -d ${database} "
                    + "--csv -c \"select ${postgresqlSelectList} from ${postgresqlTable}\" > '${file}'";
            default -> "PGPASSWORD='${password}' PGOPTIONS='-c statement_timeout=0' ${exportToolBinary} "
                    + "-v ON_ERROR_STOP=1 -h ${host} -p ${port} -U ${username} -d ${database} "
                    + "-c \"\\\\copy (select ${postgresqlSelectList} from ${postgresqlTable}) "
                    + "to '${file}' with (format csv, header true, encoding 'UTF8')\"";
        };
    }

    @Override
    protected String defaultExportBinary() {
        return "psql";
    }

    @Override
    protected String buildFailureMessage(SyncJobDefinition definition, com.example.sync.core.config.TableMapping mapping, String output) {
        if ("server_copy".equals(exportMethod(definition)) && output != null) {
            if (output.contains("pg_write_server_files") || output.contains("must be superuser")) {
                return "PostgreSQL server-side COPY requires elevated server file privileges. Prefer psql \\copy for client-side export, or grant the required pg_write_server_files/server-side file access privilege. Original output: "
                        + output;
            }
            if (output.toLowerCase().contains("permission denied")) {
                return "PostgreSQL server-side COPY writes files on the database server host. Verify the server-side path exists and the PostgreSQL service account can write to it. Original output: "
                        + output;
            }
        }
        return super.buildFailureMessage(definition, mapping, output);
    }

    private String exportMethod(SyncJobDefinition definition) {
        Map<String, String> properties = definition.fullLoad() == null ? null : definition.fullLoad().additionalProperties();
        if (properties == null) {
            return "psql_copy";
        }
        String method = properties.get("postgresExportMethod");
        if (method == null || method.isBlank()) {
            return "psql_copy";
        }
        return method.trim().toLowerCase();
    }
}
