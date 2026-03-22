package com.example.sync.admin.service;

import com.example.sync.admin.domain.CompatibilityReportEntity;
import com.example.sync.admin.domain.CompatibilityReportStatus;
import com.example.sync.admin.domain.ConnectionProfileEntity;
import com.example.sync.admin.domain.DatabaseEndpointType;
import com.example.sync.admin.dto.CompatibilityFindingResponse;
import com.example.sync.admin.dto.CompatibilityReportResponse;
import com.example.sync.admin.dto.CompatibilityReportUpsertRequest;
import com.example.sync.admin.dto.CompatibilitySummaryResponse;
import com.example.sync.admin.repository.CompatibilityReportRepository;
import com.example.sync.connectors.util.JdbcConnectionSupport;
import com.example.sync.core.config.SourceConnectionProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompatibilityReportService {

    private static final TypeReference<List<CompatibilityFindingResponse>> FINDING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<CompatibilitySummaryResponse> SUMMARY_TYPE = new TypeReference<>() {
    };

    private final CompatibilityReportRepository repository;
    private final ConnectionProfileService connectionProfileService;
    private final ConnectionProfileBindingService connectionBindingService;
    private final ObjectMapper objectMapper;

    public CompatibilityReportService(
            CompatibilityReportRepository repository,
            ConnectionProfileService connectionProfileService,
            ConnectionProfileBindingService connectionBindingService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.connectionProfileService = connectionProfileService;
        this.connectionBindingService = connectionBindingService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CompatibilityReportResponse> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CompatibilityReportResponse get(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional
    public CompatibilityReportResponse create(CompatibilityReportUpsertRequest request) {
        CompatibilityReportEntity entity = new CompatibilityReportEntity();
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public CompatibilityReportResponse update(Long id, CompatibilityReportUpsertRequest request) {
        CompatibilityReportEntity entity = findEntity(id);
        apply(entity, request);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public CompatibilityReportResponse execute(Long id) {
        CompatibilityReportEntity entity = findEntity(id);
        try {
            ConnectionProfileEntity sourceProfile = connectionBindingService.requireSourceProfile(
                    connectionProfileService.findEntity(entity.getSourceProfileId()),
                    "compatibility report scanning",
                    false
            );
            ConnectionProfileEntity targetProfile = connectionBindingService.requireTidbTargetProfile(
                    connectionProfileService.findEntity(entity.getTargetProfileId()),
                    "Compatibility report"
            );
            AnalysisResult analysisResult = analyze(sourceProfile, targetProfile);

            Path workDir = Path.of("work", "compatibility-reports", "report-" + entity.getId()).toAbsolutePath().normalize();
            Files.createDirectories(workDir);
            Path reportFile = workDir.resolve("compatibility-report.md");
            Path reportHtmlFile = workDir.resolve("compatibility-report.html");
            Files.writeString(reportFile, analysisResult.markdown());
            Files.writeString(reportHtmlFile, analysisResult.html());

            entity.setSummaryJson(writeJson(analysisResult.summary()));
            entity.setFindingsJson(writeJson(analysisResult.findings()));
            entity.setReportMarkdown(analysisResult.markdown());
            entity.setReportHtml(analysisResult.html());
            entity.setReportPath(reportFile.toString());
            entity.setReportHtmlPath(reportHtmlFile.toString());
            entity.setStatus(CompatibilityReportStatus.COMPLETED);
            entity.setLastMessage("Compatibility report completed successfully.");
            entity.setExecutedAt(Instant.now());
            return toResponse(repository.save(entity));
        } catch (Exception ex) {
            entity.setStatus(CompatibilityReportStatus.FAILED);
            entity.setLastMessage(ex.getMessage());
            entity.setExecutedAt(Instant.now());
            return toResponse(repository.save(entity));
        }
    }

    private AnalysisResult analyze(ConnectionProfileEntity sourceProfile, ConnectionProfileEntity targetProfile) throws Exception {
        List<CompatibilityFindingResponse> findings = new ArrayList<>();
        MutableSummary summary = new MutableSummary();
        SourceConnectionProperties source = connectionBindingService.toSourceProperties(sourceProfile);

        try (Connection connection = DriverManager.getConnection(
                JdbcConnectionSupport.resolveSourceJdbcUrl(source),
                source.username(),
                source.password()
        )) {
            DatabaseMetaData metaData = connection.getMetaData();
            inspectTableColumns(sourceProfile, metaData, findings, summary);
            inspectObjects(sourceProfile, connection, metaData, findings, summary);
        }

        findings.sort(Comparator.comparing(CompatibilityFindingResponse::severity).thenComparing(CompatibilityFindingResponse::objectName));
        CompatibilitySummaryResponse summaryResponse = summary.toResponse(findings.size());
        String markdown = renderMarkdown(sourceProfile, targetProfile, summaryResponse, findings);
        String html = renderHtml(sourceProfile, targetProfile, summaryResponse, findings);
        return new AnalysisResult(summaryResponse, findings, markdown, html);
    }

    private void inspectTableColumns(
            ConnectionProfileEntity sourceProfile,
            DatabaseMetaData metaData,
            List<CompatibilityFindingResponse> findings,
            MutableSummary summary
    ) throws Exception {
        String catalog = connectionBindingService.tableCatalog(sourceProfile);
        String schema = connectionBindingService.tableSchema(sourceProfile);
        try (ResultSet tables = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableCatalog = tables.getString("TABLE_CAT");
                String tableSchema = tables.getString("TABLE_SCHEM");
                String tableName = tables.getString("TABLE_NAME");
                summary.tableCount++;
                try (ResultSet columns = metaData.getColumns(tableCatalog, tableSchema, tableName, "%")) {
                    while (columns.next()) {
                        String columnName = columns.getString("COLUMN_NAME");
                        String typeName = connectionBindingService.normalizeType(columns.getString("TYPE_NAME"));
                        CompatibilityFindingResponse finding = compatibilityForColumn(sourceProfile.getDatabaseType(), tableName, columnName, typeName);
                        if (finding != null) {
                            findings.add(finding);
                            summary.accept(finding);
                        }
                    }
                }
            }
        }
    }

    private void inspectObjects(
            ConnectionProfileEntity sourceProfile,
            Connection connection,
            DatabaseMetaData metaData,
            List<CompatibilityFindingResponse> findings,
            MutableSummary summary
    ) throws Exception {
        String catalog = connectionBindingService.tableCatalog(sourceProfile);
        String schema = connectionBindingService.tableSchema(sourceProfile);
        try (ResultSet views = metaData.getTables(catalog, schema, "%", new String[]{"VIEW"})) {
            while (views.next()) {
                summary.viewCount++;
                CompatibilityFindingResponse finding = new CompatibilityFindingResponse(
                        "OBJECT",
                        "VIEW",
                        connectionBindingService.qualifiedObjectName(views.getString("TABLE_SCHEM"), views.getString("TABLE_NAME")),
                        "PARTIAL",
                        "WARN",
                        "Views require manual review before migration to TiDB.",
                        "Convert the view definition to TiDB-compatible SQL and validate dependencies."
                );
                findings.add(finding);
                summary.accept(finding);
            }
        }

        switch (sourceProfile.getDatabaseType()) {
            case MYSQL, MARIADB -> inspectMySqlFamilyObjects(sourceProfile, connection, findings, summary);
            case ORACLE -> inspectOracleObjects(sourceProfile, connection, findings, summary);
            case SQLSERVER -> inspectSqlServerObjects(sourceProfile, connection, findings, summary);
            case POSTGRESQL -> inspectPostgreSqlObjects(sourceProfile, connection, findings, summary);
            default -> {
            }
        }
    }

    private void inspectMySqlFamilyObjects(
            ConnectionProfileEntity profile,
            Connection connection,
            List<CompatibilityFindingResponse> findings,
            MutableSummary summary
    ) throws Exception {
        String database = profile.getDatabaseName();
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from information_schema.triggers where trigger_schema = '" + escapeSql(database) + "'"),
                "TRIGGER", "Triggers need manual rewrite because TiDB trigger compatibility is limited.", true);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from information_schema.routines where routine_schema = '" + escapeSql(database) + "'"),
                "ROUTINE", "Stored procedures and functions need manual rewrite before migrating to TiDB.", true);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from information_schema.partitions where table_schema = '" + escapeSql(database) + "' and partition_name is not null"),
                "PARTITIONED_TABLE", "Partitioned tables need partition strategy review before migrating to TiDB.", false);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from information_schema.columns where table_schema = '" + escapeSql(database) + "' and extra like '%GENERATED%'"),
                "GENERATED_COLUMN", "Generated columns require expression compatibility review in TiDB.", false);
    }

    private void inspectOracleObjects(
            ConnectionProfileEntity profile,
            Connection connection,
            List<CompatibilityFindingResponse> findings,
            MutableSummary summary
    ) throws Exception {
        String owner = connectionBindingService.oracleOwner(profile);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from all_triggers where owner = '" + escapeSql(owner) + "'"),
                "TRIGGER", "Oracle triggers need manual rewrite because TiDB trigger compatibility is limited.", true);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from all_objects where owner = '" + escapeSql(owner) + "' and object_type in ('PROCEDURE','PACKAGE','PACKAGE BODY')"),
                "PROCEDURE_OR_PACKAGE", "Oracle procedures and packages cannot be migrated directly to TiDB.", true);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from all_objects where owner = '" + escapeSql(owner) + "' and object_type = 'FUNCTION'"),
                "FUNCTION", "Oracle functions need SQL compatibility review before migrating to TiDB.", false);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from all_sequences where sequence_owner = '" + escapeSql(owner) + "'"),
                "SEQUENCE", "Oracle sequences should be reviewed and mapped to TiDB sequence or auto-increment strategy.", false);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from all_mviews where owner = '" + escapeSql(owner) + "'"),
                "MATERIALIZED_VIEW", "Oracle materialized views require manual redesign before migrating to TiDB.", true);
    }

    private void inspectSqlServerObjects(
            ConnectionProfileEntity profile,
            Connection connection,
            List<CompatibilityFindingResponse> findings,
            MutableSummary summary
    ) throws Exception {
        String schema = connectionBindingService.firstNonBlank(profile.getSchemaName(), "dbo");
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from sys.triggers"),
                "TRIGGER", "SQL Server triggers need manual rewrite because TiDB trigger compatibility is limited.", true);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from sys.procedures"),
                "PROCEDURE", "SQL Server stored procedures cannot be migrated directly to TiDB.", true);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from sys.sequences"),
                "SEQUENCE", "SQL Server sequences need strategy review before migrating to TiDB.", false);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from sys.columns c join sys.tables t on c.object_id=t.object_id join sys.schemas s on t.schema_id=s.schema_id where s.name='"
                        + escapeSql(schema) + "' and c.is_computed = 1"),
                "COMPUTED_COLUMN", "Computed columns require expression compatibility review in TiDB.", false);
    }

    private void inspectPostgreSqlObjects(
            ConnectionProfileEntity profile,
            Connection connection,
            List<CompatibilityFindingResponse> findings,
            MutableSummary summary
    ) throws Exception {
        String schema = connectionBindingService.firstNonBlank(profile.getSchemaName(), "public");
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from information_schema.triggers where trigger_schema = '" + escapeSql(schema) + "'"),
                "TRIGGER", "PostgreSQL triggers need manual rewrite because TiDB trigger compatibility is limited.", true);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from information_schema.routines where specific_schema = '" + escapeSql(schema) + "'"),
                "ROUTINE", "PostgreSQL functions and procedures need manual rewrite before migrating to TiDB.", true);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from information_schema.sequences where sequence_schema = '" + escapeSql(schema) + "'"),
                "SEQUENCE", "PostgreSQL sequences should be reviewed and mapped to TiDB sequence or auto-increment strategy.", false);
        addCountBasedFinding(findings, summary, countQuery(connection,
                "select count(*) from information_schema.columns where table_schema = '" + escapeSql(schema) + "' and data_type = 'ARRAY'"),
                "ARRAY_COLUMN", "PostgreSQL array columns are not directly compatible with TiDB relational types.", true);
    }

    private CompatibilityFindingResponse compatibilityForColumn(
            DatabaseEndpointType databaseType,
            String tableName,
            String columnName,
            String sourceType
    ) {
        String objectName = tableName + "." + columnName;
        if (databaseType == DatabaseEndpointType.ORACLE) {
            if (matchesAny(sourceType, "XMLTYPE", "BFILE", "UROWID", "ROWID")) {
                return incompatibleType(objectName, sourceType, "Map this column to VARCHAR, JSON, or BLOB manually before schema sync.");
            }
            if (sourceType.startsWith("TIMESTAMP WITH TIME ZONE") || sourceType.startsWith("TIMESTAMP WITH LOCAL TIME ZONE")) {
                return incompatibleType(objectName, sourceType, "Review timezone-aware Oracle timestamp columns carefully to avoid losing original zone semantics in TiDB.");
            }
            if (sourceType.startsWith("INTERVAL")) {
                return incompatibleType(objectName, sourceType, "Convert Oracle INTERVAL columns to VARCHAR or numeric duration fields.");
            }
        }
        if (databaseType == DatabaseEndpointType.SQLSERVER && matchesAny(sourceType, "SQL_VARIANT", "HIERARCHYID", "GEOGRAPHY", "GEOMETRY")) {
            return incompatibleType(objectName, sourceType, "Review custom or spatial SQL Server types and remodel them before migrating to TiDB.");
        }
        if (databaseType == DatabaseEndpointType.SQLSERVER && matchesAny(sourceType, "DATETIMEOFFSET")) {
            return incompatibleType(objectName, sourceType, "Review SQL Server DATETIMEOFFSET columns to preserve zone and offset semantics before migrating to TiDB.");
        }
        if (databaseType == DatabaseEndpointType.POSTGRESQL && matchesAny(sourceType, "JSONB", "UUID", "BYTEA")) {
            return new CompatibilityFindingResponse(
                    "DATA_TYPE",
                    "COLUMN",
                    objectName,
                    "PARTIAL",
                    "WARN",
                    "Source type " + sourceType + " is generally workable but needs explicit target mapping review in TiDB.",
                "Validate schema mapping and downstream application compatibility before execution."
            );
        }
        if (databaseType == DatabaseEndpointType.POSTGRESQL && matchesAny(sourceType, "TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE", "TIMETZ", "TIME WITH TIME ZONE")) {
            return incompatibleType(objectName, sourceType, "Review PostgreSQL timezone-aware temporal columns because TiDB does not preserve the original zone name or offset in the same way.");
        }
        if ((databaseType == DatabaseEndpointType.MYSQL || databaseType == DatabaseEndpointType.MARIADB)
                && matchesAny(sourceType, "SET", "GEOMETRY", "MULTIPOLYGON", "LINESTRING")) {
            return new CompatibilityFindingResponse(
                    "DATA_TYPE",
                    "COLUMN",
                    objectName,
                    "PARTIAL",
                    "WARN",
                    "Source type " + sourceType + " needs manual compatibility review before loading into TiDB.",
                    "Consider converting the column to VARCHAR, JSON, or another TiDB-supported type."
            );
        }
        return null;
    }

    private CompatibilityFindingResponse incompatibleType(String objectName, String sourceType, String suggestion) {
        return new CompatibilityFindingResponse(
                "DATA_TYPE",
                "COLUMN",
                objectName,
                "INCOMPATIBLE",
                "ERROR",
                "Source type " + sourceType + " is not directly supported by TiDB.",
                suggestion
        );
    }

    private void addCountBasedFinding(
            List<CompatibilityFindingResponse> findings,
            MutableSummary summary,
            int count,
            String objectType,
            String message,
            boolean incompatible
    ) {
        if (count <= 0) {
            return;
        }
        if ("TRIGGER".equals(objectType)) {
            summary.triggerCount += count;
        } else if ("PROCEDURE".equals(objectType) || "PROCEDURE_OR_PACKAGE".equals(objectType) || "ROUTINE".equals(objectType)) {
            summary.procedureCount += count;
        } else if ("FUNCTION".equals(objectType)) {
            summary.functionCount += count;
        } else if ("SEQUENCE".equals(objectType)) {
            summary.sequenceCount += count;
        }
        CompatibilityFindingResponse finding = new CompatibilityFindingResponse(
                "OBJECT",
                objectType,
                objectType + " x" + count,
                incompatible ? "INCOMPATIBLE" : "PARTIAL",
                incompatible ? "ERROR" : "WARN",
                message,
                "Review each " + objectType.toLowerCase(Locale.ROOT) + " manually and plan equivalent implementation on TiDB."
        );
        findings.add(finding);
        summary.accept(finding);
    }

    private int countQuery(Connection connection, String sql) {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (Exception ignored) {
            return 0;
        }
        return 0;
    }

    private String renderMarkdown(
            ConnectionProfileEntity sourceProfile,
            ConnectionProfileEntity targetProfile,
            CompatibilitySummaryResponse summary,
            List<CompatibilityFindingResponse> findings
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Compatibility Report\n\n");
        builder.append("- Source: ").append(sourceProfile.getName()).append(" / ").append(sourceProfile.getDatabaseType()).append('\n');
        builder.append("- Target: ").append(targetProfile.getName()).append(" / TiDB\n");
        builder.append("- Executed At: ").append(Instant.now()).append("\n\n");
        builder.append("## Summary\n\n");
        builder.append("- Total Findings: ").append(summary.totalFindings()).append('\n');
        builder.append("- Incompatible: ").append(summary.incompatibleCount()).append('\n');
        builder.append("- Partial: ").append(summary.partialCount()).append('\n');
        builder.append("- Compatible: ").append(summary.compatibleCount()).append('\n');
        builder.append("- Tables: ").append(summary.tableCount()).append('\n');
        builder.append("- Views: ").append(summary.viewCount()).append('\n');
        builder.append("- Triggers: ").append(summary.triggerCount()).append('\n');
        builder.append("- Procedures: ").append(summary.procedureCount()).append('\n');
        builder.append("- Functions: ").append(summary.functionCount()).append('\n');
        builder.append("- Sequences: ").append(summary.sequenceCount()).append("\n\n");
        builder.append("## Findings\n\n");
        if (findings.isEmpty()) {
            builder.append("- No incompatible objects were detected by the current scanner.\n");
        } else {
            for (CompatibilityFindingResponse finding : findings) {
                builder.append("- [")
                        .append(finding.severity())
                        .append("] ")
                        .append(finding.objectType())
                        .append(" `")
                        .append(finding.objectName())
                        .append("` => ")
                        .append(finding.compatibility())
                        .append(". ")
                        .append(finding.message())
                        .append(" Suggestion: ")
                        .append(finding.suggestion())
                        .append('\n');
            }
        }
        return builder.toString();
    }

    private String renderHtml(
            ConnectionProfileEntity sourceProfile,
            ConnectionProfileEntity targetProfile,
            CompatibilitySummaryResponse summary,
            List<CompatibilityFindingResponse> findings
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                .append("<title>Compatibility Report</title>")
                .append("<style>")
                .append("body{font-family:Arial,\"PingFang SC\",sans-serif;background:#f6f8fb;color:#1b2733;margin:0;padding:32px;}")
                .append(".wrap{max-width:1200px;margin:0 auto;display:grid;gap:24px;}")
                .append(".card{background:#fff;border:1px solid #d8e1eb;border-radius:20px;padding:20px;box-shadow:0 12px 28px rgba(16,37,63,.08);}")
                .append(".grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;}")
                .append(".metric{padding:14px 16px;border-radius:16px;background:#f6f8fb;border:1px solid #e4ebf2;}")
                .append(".metric strong{display:block;font-size:24px;margin-top:6px;}")
                .append("table{width:100%;border-collapse:collapse;font-size:14px;}")
                .append("th,td{padding:12px;border-bottom:1px solid #e8edf3;text-align:left;vertical-align:top;}")
                .append("th{color:#5f7285;font-weight:600;}")
                .append(".sev-ERROR{color:#a63d3d;font-weight:700;}.sev-WARN{color:#9b5f00;font-weight:700;}.sev-INFO{color:#0f5d88;font-weight:700;}")
                .append("@media (max-width:900px){.grid{grid-template-columns:repeat(2,minmax(0,1fr));}}")
                .append("</style></head><body><div class=\"wrap\">");
        builder.append("<section class=\"card\"><h1>Compatibility Report for TiDB Migration</h1>")
                .append("<p>Source: ").append(escapeHtml(sourceProfile.getName())).append(" (")
                .append(escapeHtml(sourceProfile.getDatabaseType().name())).append(")")
                .append(" &nbsp;&rarr;&nbsp; Target: ").append(escapeHtml(targetProfile.getName())).append(" (TiDB)</p></section>");
        builder.append("<section class=\"card\"><div class=\"grid\">")
                .append(metricHtml("Total findings", summary.totalFindings()))
                .append(metricHtml("Incompatible", summary.incompatibleCount()))
                .append(metricHtml("Needs review", summary.partialCount()))
                .append(metricHtml("Tables", summary.tableCount()))
                .append(metricHtml("Views", summary.viewCount()))
                .append(metricHtml("Triggers", summary.triggerCount()))
                .append(metricHtml("Procedures", summary.procedureCount()))
                .append(metricHtml("Sequences", summary.sequenceCount()))
                .append("</div></section>");
        builder.append("<section class=\"card\"><h2>Findings</h2><table><thead><tr>")
                .append("<th>Severity</th><th>Object type</th><th>Object name</th><th>Issue</th><th>Suggested action</th>")
                .append("</tr></thead><tbody>");
        if (findings.isEmpty()) {
            builder.append("<tr><td colspan=\"5\">No compatibility issues were detected.</td></tr>");
        } else {
            for (CompatibilityFindingResponse finding : findings) {
                builder.append("<tr>")
                        .append("<td class=\"sev-").append(escapeHtml(finding.severity())).append("\">").append(escapeHtml(finding.severity())).append("</td>")
                        .append("<td>").append(escapeHtml(finding.objectType())).append("</td>")
                        .append("<td>").append(escapeHtml(finding.objectName())).append("</td>")
                        .append("<td>").append(escapeHtml(finding.message())).append("</td>")
                        .append("<td>").append(escapeHtml(finding.suggestion())).append("</td>")
                        .append("</tr>");
            }
        }
        builder.append("</tbody></table></section></div></body></html>");
        return builder.toString();
    }

    private CompatibilityReportEntity findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Compatibility report not found: " + id));
    }

    private void apply(CompatibilityReportEntity entity, CompatibilityReportUpsertRequest request) {
        entity.setName(request.name());
        entity.setSourceProfileId(request.sourceProfileId());
        entity.setTargetProfileId(request.targetProfileId());
        if (entity.getStatus() == null || entity.getStatus() != CompatibilityReportStatus.DRAFT) {
            entity.setStatus(CompatibilityReportStatus.DRAFT);
        }
        entity.setLastMessage("Compatibility report task saved.");
    }

    private CompatibilityReportResponse toResponse(CompatibilityReportEntity entity) {
        return new CompatibilityReportResponse(
                entity.getId(),
                entity.getName(),
                entity.getSourceProfileId(),
                entity.getTargetProfileId(),
                entity.getStatus(),
                entity.getLastMessage(),
                readJson(entity.getSummaryJson(), SUMMARY_TYPE, new CompatibilitySummaryResponse(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
                readJson(entity.getFindingsJson(), FINDING_LIST, List.of()),
                entity.getReportMarkdown(),
                entity.getReportHtml(),
                entity.getReportPath(),
                entity.getReportHtmlPath(),
                entity.getExecutedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String metricHtml(String label, int value) {
        return "<div class=\"metric\"><span>" + escapeHtml(label) + "</span><strong>" + value + "</strong></div>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private boolean matchesAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String escapeSql(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize compatibility report payload", ex);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type, T defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize compatibility report payload", ex);
        }
    }

    private record AnalysisResult(
            CompatibilitySummaryResponse summary,
            List<CompatibilityFindingResponse> findings,
            String markdown,
            String html
    ) {
    }

    private static final class MutableSummary {
        private int incompatibleCount;
        private int partialCount;
        private int compatibleCount;
        private int errorCount;
        private int warningCount;
        private int infoCount;
        private int tableCount;
        private int viewCount;
        private int triggerCount;
        private int procedureCount;
        private int functionCount;
        private int sequenceCount;

        private void accept(CompatibilityFindingResponse finding) {
            switch (finding.compatibility()) {
                case "INCOMPATIBLE" -> incompatibleCount++;
                case "PARTIAL" -> partialCount++;
                default -> compatibleCount++;
            }
            switch (finding.severity()) {
                case "ERROR" -> errorCount++;
                case "WARN" -> warningCount++;
                default -> infoCount++;
            }
        }

        private CompatibilitySummaryResponse toResponse(int totalFindings) {
            return new CompatibilitySummaryResponse(
                    totalFindings,
                    incompatibleCount,
                    partialCount,
                    compatibleCount,
                    errorCount,
                    warningCount,
                    infoCount,
                    tableCount,
                    viewCount,
                    triggerCount,
                    procedureCount,
                    functionCount,
                    sequenceCount
            );
        }
    }
}
