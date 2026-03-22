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
        if (usesSqlcmd(definition)) {
            return "${exportToolBinary} -S ${host},${port} -U ${username} -P '${password}' -d ${database} "
                    + "-w 65535 -y 0 -Y 0 -s, -h -1 "
                    + "-Q \"SET NOCOUNT ON; SELECT ${sqlServerSelectList} FROM ${sqlServerTable}\" "
                    + "-o '${file}'";
        }
        return "${exportToolBinary} \"SET NOCOUNT ON; SELECT ${sqlServerSelectList} FROM ${sqlServerTable}\" "
                + "queryout '${file}' -c -t, -r 0x0A -S ${host},${port} -U ${username} -P '${password}' -d ${database}";
    }

    @Override
    protected String defaultExportBinary() {
        return null;
    }

    @Override
    protected String resolveExportBinary(SyncJobDefinition definition, String defaultBinary) {
        return super.resolveExportBinary(definition, preferredExportBinary(definition));
    }

    @Override
    protected boolean exportProducesHeader(SyncJobDefinition definition) {
        return false;
    }

    private String preferredExportBinary(SyncJobDefinition definition) {
        return usesSqlcmd(definition) ? "sqlcmd" : "bcp";
    }

    private boolean usesSqlcmd(SyncJobDefinition definition) {
        if (definition.fullLoad() == null || definition.fullLoad().additionalProperties() == null) {
            return false;
        }
        String tool = definition.fullLoad().additionalProperties().get("sqlServerExportTool");
        return tool != null && "sqlcmd".equalsIgnoreCase(tool.trim());
    }
}
