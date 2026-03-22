package com.example.sync.admin.service;

import com.example.sync.admin.domain.ConnectionProfileEntity;
import com.example.sync.admin.domain.ConnectionProfileRole;
import com.example.sync.admin.domain.DatabaseEndpointType;
import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.TargetConnectionProperties;
import com.example.sync.core.model.SourceDatabaseType;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ConnectionProfileBindingService {

    public ConnectionProfileEntity requireSourceProfile(ConnectionProfileEntity entity, String capabilityName, boolean allowCsv) {
        if (entity.getRole() != ConnectionProfileRole.SOURCE) {
            throw new IllegalArgumentException("Selected source profile is not a SOURCE profile.");
        }
        if (!allowCsv && entity.getDatabaseType() == DatabaseEndpointType.CSV) {
            throw new IllegalArgumentException("CSV source does not support " + capabilityName + ".");
        }
        return entity;
    }

    public ConnectionProfileEntity requireTidbTargetProfile(ConnectionProfileEntity entity, String capabilityName) {
        if (entity.getRole() != ConnectionProfileRole.TARGET) {
            throw new IllegalArgumentException("Selected target profile is not a TARGET profile.");
        }
        if (entity.getDatabaseType() != DatabaseEndpointType.TIDB) {
            throw new IllegalArgumentException(capabilityName + " target must be TiDB.");
        }
        return entity;
    }

    public SourceConnectionProperties toSourceProperties(ConnectionProfileEntity entity) {
        return new SourceConnectionProperties(
                toSourceDatabaseType(entity.getDatabaseType()),
                entity.getHost(),
                entity.getPort(),
                entity.getDatabaseName(),
                entity.getSchemaName(),
                entity.getUsername(),
                entity.getPassword(),
                entity.getJdbcUrl(),
                entity.getJdbcParameters(),
                ""
        );
    }

    public TargetConnectionProperties toTargetProperties(ConnectionProfileEntity entity, String lightningBinary) {
        return new TargetConnectionProperties(
                entity.getHost(),
                entity.getPort(),
                entity.getDatabaseName(),
                entity.getUsername(),
                entity.getPassword(),
                entity.getJdbcUrl(),
                entity.getJdbcParameters(),
                lightningBinary,
                entity.getTidbStatusPort() == null ? 10080 : entity.getTidbStatusPort()
        );
    }

    public SourceDatabaseType toSourceDatabaseType(DatabaseEndpointType databaseType) {
        return switch (databaseType) {
            case CSV -> SourceDatabaseType.CSV;
            case MYSQL -> SourceDatabaseType.MYSQL;
            case MARIADB -> SourceDatabaseType.MARIADB;
            case ORACLE -> SourceDatabaseType.ORACLE;
            case SQLSERVER -> SourceDatabaseType.SQLSERVER;
            case POSTGRESQL -> SourceDatabaseType.POSTGRESQL;
            case DB2 -> SourceDatabaseType.DB2;
            case HANA -> SourceDatabaseType.HANA;
            case MONGODB -> SourceDatabaseType.MONGODB;
            case TIDB -> throw new IllegalArgumentException("TIDB is not a valid source database type.");
        };
    }

    public String tableCatalog(ConnectionProfileEntity sourceProfile) {
        return switch (sourceProfile.getDatabaseType()) {
            case MYSQL, MARIADB, SQLSERVER -> blankToNull(sourceProfile.getDatabaseName());
            default -> null;
        };
    }

    public String tableSchema(ConnectionProfileEntity sourceProfile) {
        return switch (sourceProfile.getDatabaseType()) {
            case ORACLE, HANA, DB2 -> upperBlankToNull(firstNonBlank(sourceProfile.getSchemaName(), sourceProfile.getUsername()));
            case POSTGRESQL -> blankToNull(firstNonBlank(sourceProfile.getSchemaName(), "public"));
            case SQLSERVER -> blankToNull(firstNonBlank(sourceProfile.getSchemaName(), "dbo"));
            case MYSQL, MARIADB -> blankToNull(firstNonBlank(sourceProfile.getSchemaName(), sourceProfile.getDatabaseName()));
            default -> null;
        };
    }

    public String oracleOwner(ConnectionProfileEntity sourceProfile) {
        return upperBlankToNull(firstNonBlank(sourceProfile.getSchemaName(), sourceProfile.getUsername()));
    }

    public String qualifiedObjectName(String schema, String name) {
        String resolvedSchema = blankToNull(schema);
        return resolvedSchema == null ? name : resolvedSchema + "." + name;
    }

    public String normalizeType(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public String upperBlankToNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    public String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
