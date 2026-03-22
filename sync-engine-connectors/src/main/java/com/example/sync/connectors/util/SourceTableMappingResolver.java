package com.example.sync.connectors.util;

import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.spi.ProgressReporter;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SourceTableMappingResolver {

    public SyncJobDefinition resolve(SyncJobDefinition definition, ProgressReporter reporter) throws Exception {
        if (definition.tableMappings() != null && !definition.tableMappings().isEmpty()) {
            return definition;
        }

        List<TableMapping> discoveredMappings = switch (definition.source().databaseType()) {
            case CSV -> definition.tableMappings();
            case MONGODB -> discoverMongoMappings(definition);
            default -> discoverJdbcMappings(definition);
        };

        if (discoveredMappings.isEmpty()) {
            throw new IllegalArgumentException("No source tables found under selected database/schema");
        }

        reporter.log("INFO", "Discovered " + discoveredMappings.size() + " source tables for database-wide sync");
        return new SyncJobDefinition(
                definition.jobId(),
                definition.jobName(),
                definition.syncMode(),
                definition.deploymentArchitecture(),
                definition.source(),
                definition.target(),
                discoveredMappings,
                definition.fullLoad(),
                definition.incremental()
        );
    }

    List<TableMapping> discoverJdbcMappings(SyncJobDefinition definition) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                JdbcConnectionSupport.resolveSourceJdbcUrl(definition.source()),
                definition.source().username(),
                definition.source().password()
        )) {
            return discoverJdbcMappings(definition, connection.getMetaData());
        }
    }

    List<TableMapping> discoverJdbcMappings(SyncJobDefinition definition, DatabaseMetaData metaData) throws Exception {
        SourceConnectionProperties source = definition.source();
        SourceDatabaseType databaseType = source.databaseType();
        String catalog = tableCatalog(databaseType, source);
        String schema = tableSchema(databaseType, source);
        Map<String, List<String>> primaryKeysByTable = new LinkedHashMap<>();
        List<TableMapping> mappings = new ArrayList<>();

        try (ResultSet tables = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableCatalog = tables.getString("TABLE_CAT");
                String tableSchema = tables.getString("TABLE_SCHEM");
                String tableName = tables.getString("TABLE_NAME");
                if (tableName == null || tableName.isBlank()) {
                    continue;
                }
                List<String> primaryKeys = primaryKeysByTable.computeIfAbsent(
                        tableName,
                        ignored -> loadPrimaryKeys(metaData, tableCatalog, tableSchema, tableName)
                );
                mappings.add(new TableMapping(
                        normalizeCatalog(databaseType, source, tableCatalog),
                        normalizeSchema(databaseType, source, tableSchema),
                        tableName,
                        definition.target().databaseName(),
                        tableName,
                        primaryKeys,
                        "",
                        List.of(),
                        Map.of()
                ));
            }
        }

        mappings.sort(Comparator.comparing(TableMapping::sourceTable, String.CASE_INSENSITIVE_ORDER));
        return mappings;
    }

    List<TableMapping> discoverMongoMappings(SyncJobDefinition definition) {
        String connectionString = MongoConnectionSupport.resolveConnectionString(
                definition.source(),
                definition.incremental().additionalProperties()
        );
        try (MongoClient client = MongoClients.create(connectionString)) {
            MongoDatabase database = client.getDatabase(definition.source().databaseName());
            List<TableMapping> mappings = new ArrayList<>();
            for (String collectionName : database.listCollectionNames()) {
                mappings.add(new TableMapping(
                        definition.source().databaseName(),
                        definition.source().databaseName(),
                        collectionName,
                        definition.target().databaseName(),
                        collectionName,
                        List.of("_id"),
                        "",
                        List.of(),
                        Map.of()
                ));
            }
            mappings.sort(Comparator.comparing(TableMapping::sourceTable, String.CASE_INSENSITIVE_ORDER));
            return mappings;
        }
    }

    private List<String> loadPrimaryKeys(DatabaseMetaData metaData, String catalog, String schema, String tableName) {
        Map<Short, String> sorted = new LinkedHashMap<>();
        try (ResultSet primaryKeys = metaData.getPrimaryKeys(catalog, schema, tableName)) {
            while (primaryKeys.next()) {
                sorted.put(primaryKeys.getShort("KEY_SEQ"), primaryKeys.getString("COLUMN_NAME"));
            }
        } catch (Exception ignored) {
            return List.of();
        }
        Set<Short> order = new LinkedHashSet<>(sorted.keySet());
        return order.stream().sorted().map(sorted::get).filter(value -> value != null && !value.isBlank()).toList();
    }

    private String tableCatalog(SourceDatabaseType databaseType, SourceConnectionProperties source) {
        return switch (databaseType) {
            case MYSQL, MARIADB, SQLSERVER -> blankToNull(source.databaseName());
            default -> null;
        };
    }

    private String tableSchema(SourceDatabaseType databaseType, SourceConnectionProperties source) {
        return switch (databaseType) {
            case ORACLE, HANA, DB2 -> upperBlankToNull(firstNonBlank(source.schemaName(), source.username()));
            case POSTGRESQL -> blankToNull(firstNonBlank(source.schemaName(), "public"));
            case SQLSERVER -> blankToNull(firstNonBlank(source.schemaName(), "dbo"));
            case MYSQL, MARIADB -> blankToNull(firstNonBlank(source.schemaName(), source.databaseName()));
            default -> null;
        };
    }

    private String normalizeCatalog(SourceDatabaseType databaseType, SourceConnectionProperties source, String discoveredCatalog) {
        return switch (databaseType) {
            case MYSQL, MARIADB, SQLSERVER -> firstNonBlank(discoveredCatalog, source.databaseName());
            default -> firstNonBlank(source.databaseName(), discoveredCatalog);
        };
    }

    private String normalizeSchema(SourceDatabaseType databaseType, SourceConnectionProperties source, String discoveredSchema) {
        return switch (databaseType) {
            case MYSQL, MARIADB -> firstNonBlank(discoveredSchema, source.schemaName(), source.databaseName());
            case ORACLE, HANA, DB2 -> firstNonBlank(discoveredSchema, upperBlankToNull(source.schemaName()), upperBlankToNull(source.username()));
            case POSTGRESQL, SQLSERVER -> firstNonBlank(discoveredSchema, source.schemaName());
            default -> firstNonBlank(source.schemaName(), discoveredSchema, source.databaseName());
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String upperBlankToNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
