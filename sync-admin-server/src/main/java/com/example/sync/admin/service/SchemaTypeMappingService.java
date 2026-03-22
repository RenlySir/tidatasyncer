package com.example.sync.admin.service;

import com.example.sync.admin.domain.DatabaseEndpointType;
import com.example.sync.admin.dto.UnsupportedTypeItemResponse;
import java.sql.Types;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SchemaTypeMappingService {

    public ColumnMappingResult mapColumn(
            DatabaseEndpointType sourceType,
            String tableName,
            SourceColumnDefinition column,
            Map<String, String> overrideMappings
    ) {
        String override = firstNonBlank(
                overrideMappings.get((tableName + "." + column.columnName()).toLowerCase(Locale.ROOT)),
                overrideMappings.get(normalizedType(column.typeName()))
        );
        if (override != null) {
            return new ColumnMappingResult(override, null);
        }

        String typeName = normalizedType(column.typeName());
        return switch (sourceType) {
            case ORACLE -> mapOracleColumn(tableName, column, typeName);
            case MYSQL, MARIADB -> mapMySqlFamilyColumn(tableName, column, typeName);
            case SQLSERVER -> mapSqlServerColumn(tableName, column, typeName);
            case POSTGRESQL -> mapPostgreSqlColumn(tableName, column, typeName);
            default -> mapGenericColumn(tableName, column, typeName);
        };
    }

    private ColumnMappingResult mapOracleColumn(String tableName, SourceColumnDefinition column, String typeName) {
        if (typeName.startsWith("TIMESTAMP WITH TIME ZONE") || typeName.startsWith("TIMESTAMP WITH LOCAL TIME ZONE")) {
            return unsupported(tableName, column, "VARCHAR(64)", "Oracle timezone-aware timestamp types need manual conversion because TiDB does not retain the original zone name or offset semantics.");
        }
        return switch (typeName) {
            case "CHAR", "NCHAR" -> new ColumnMappingResult("CHAR(" + safeLength(column.columnSize(), 1, 255) + ")", null);
            case "VARCHAR", "VARCHAR2", "NVARCHAR2" -> new ColumnMappingResult(varcharOrText(column.columnSize()), null);
            case "CLOB", "NCLOB", "LONG" -> new ColumnMappingResult("LONGTEXT", null);
            case "RAW" -> new ColumnMappingResult(varbinaryOrBlob(column.columnSize()), null);
            case "BLOB", "LONG RAW" -> new ColumnMappingResult("LONGBLOB", null);
            case "DATE" -> new ColumnMappingResult("DATETIME", null);
            case "TIMESTAMP", "TIMESTAMP(6)" -> new ColumnMappingResult("DATETIME(6)", null);
            case "FLOAT", "BINARY_FLOAT", "BINARY_DOUBLE" -> new ColumnMappingResult("DOUBLE", null);
            case "NUMBER", "DECIMAL", "NUMERIC" -> new ColumnMappingResult(numericTargetType(column.columnSize(), column.decimalDigits()), null);
            case "JSON" -> new ColumnMappingResult("JSON", null);
            case "XMLTYPE" -> unsupported(tableName, column, "JSON", "Oracle XMLTYPE requires manual XML to JSON or TEXT conversion before loading into TiDB.");
            case "UROWID", "ROWID" -> unsupported(tableName, column, "VARCHAR(255)", "Oracle ROWID family is not a native TiDB type.");
            case "BFILE" -> unsupported(tableName, column, "VARCHAR(1024)", "Oracle BFILE points to external files and must be remodeled manually.");
            default -> {
                if (typeName.startsWith("INTERVAL")) {
                    yield unsupported(tableName, column, "VARCHAR(64)", "Oracle INTERVAL types need manual normalization.");
                }
                yield mapGenericColumn(tableName, column, typeName);
            }
        };
    }

    private ColumnMappingResult mapMySqlFamilyColumn(String tableName, SourceColumnDefinition column, String typeName) {
        if (typeName.contains("UNSIGNED")) {
            return mapMySqlUnsignedColumn(tableName, column, typeName);
        }
        return switch (typeName) {
            case "BIT" -> new ColumnMappingResult(column.columnSize() <= 1 ? "BOOLEAN" : "BIT(" + safeLength(column.columnSize(), 1, 64) + ")", null);
            case "BOOLEAN", "BOOL" -> new ColumnMappingResult("BOOLEAN", null);
            case "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT" -> new ColumnMappingResult(normalizedIntegerType(typeName), null);
            case "DECIMAL", "NUMERIC" -> new ColumnMappingResult(decimalTargetType(column.columnSize(), column.decimalDigits(), 65, 30), null);
            case "FLOAT" -> new ColumnMappingResult("FLOAT", null);
            case "DOUBLE", "REAL" -> new ColumnMappingResult("DOUBLE", null);
            case "CHAR" -> new ColumnMappingResult("CHAR(" + safeLength(column.columnSize(), 1, 255) + ")", null);
            case "VARCHAR" -> new ColumnMappingResult(varcharOrText(column.columnSize()), null);
            case "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT" -> new ColumnMappingResult(typeName, null);
            case "BINARY" -> new ColumnMappingResult("BINARY(" + safeLength(column.columnSize(), 1, 255) + ")", null);
            case "VARBINARY" -> new ColumnMappingResult(varbinaryOrBlob(column.columnSize()), null);
            case "TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB" -> new ColumnMappingResult(typeName, null);
            case "DATE" -> new ColumnMappingResult("DATE", null);
            case "TIME" -> new ColumnMappingResult(temporalWithPrecision("TIME", column.decimalDigits()), null);
            case "DATETIME" -> new ColumnMappingResult(temporalWithPrecision("DATETIME", column.decimalDigits()), null);
            case "TIMESTAMP" -> new ColumnMappingResult(temporalWithPrecision("TIMESTAMP", column.decimalDigits()), null);
            case "YEAR" -> new ColumnMappingResult("YEAR", null);
            case "JSON" -> new ColumnMappingResult("JSON", null);
            default -> mapGenericColumn(tableName, column, typeName);
        };
    }

    private ColumnMappingResult mapMySqlUnsignedColumn(String tableName, SourceColumnDefinition column, String typeName) {
        if (typeName.startsWith("TINYINT")) {
            return new ColumnMappingResult("TINYINT UNSIGNED", null);
        }
        if (typeName.startsWith("SMALLINT")) {
            return new ColumnMappingResult("SMALLINT UNSIGNED", null);
        }
        if (typeName.startsWith("MEDIUMINT")) {
            return new ColumnMappingResult("MEDIUMINT UNSIGNED", null);
        }
        if (typeName.startsWith("INT") || typeName.startsWith("INTEGER")) {
            return new ColumnMappingResult("INT UNSIGNED", null);
        }
        if (typeName.startsWith("BIGINT")) {
            return new ColumnMappingResult("BIGINT UNSIGNED", null);
        }
        return mapGenericColumn(tableName, column, typeName);
    }

    private ColumnMappingResult mapSqlServerColumn(String tableName, SourceColumnDefinition column, String typeName) {
        return switch (typeName) {
            case "BIT" -> new ColumnMappingResult("BOOLEAN", null);
            case "TINYINT" -> new ColumnMappingResult("TINYINT UNSIGNED", null);
            case "SMALLINT" -> new ColumnMappingResult("SMALLINT", null);
            case "INT", "INTEGER" -> new ColumnMappingResult("INT", null);
            case "BIGINT" -> new ColumnMappingResult("BIGINT", null);
            case "DECIMAL", "NUMERIC" -> new ColumnMappingResult(decimalTargetType(column.columnSize(), column.decimalDigits(), 65, 30), null);
            case "MONEY" -> new ColumnMappingResult("DECIMAL(19,4)", null);
            case "SMALLMONEY" -> new ColumnMappingResult("DECIMAL(10,4)", null);
            case "FLOAT", "REAL" -> new ColumnMappingResult("DOUBLE", null);
            case "CHAR", "NCHAR" -> new ColumnMappingResult("CHAR(" + safeLength(column.columnSize(), 1, 255) + ")", null);
            case "VARCHAR", "NVARCHAR" -> new ColumnMappingResult(varcharOrText(column.columnSize()), null);
            case "TEXT", "NTEXT" -> new ColumnMappingResult("LONGTEXT", null);
            case "BINARY" -> new ColumnMappingResult("BINARY(" + safeLength(column.columnSize(), 1, 255) + ")", null);
            case "VARBINARY", "IMAGE" -> new ColumnMappingResult("LONGBLOB", null);
            case "DATE" -> new ColumnMappingResult("DATE", null);
            case "TIME" -> new ColumnMappingResult(temporalWithPrecision("TIME", column.decimalDigits()), null);
            case "DATETIME", "DATETIME2", "SMALLDATETIME" -> new ColumnMappingResult("DATETIME(6)", null);
            case "DATETIMEOFFSET" -> unsupported(tableName, column, "DATETIME(6)", "SQL Server DATETIMEOFFSET carries time zone semantics that need manual review before migrating to TiDB.");
            case "UNIQUEIDENTIFIER" -> new ColumnMappingResult("CHAR(36)", null);
            case "XML" -> unsupported(tableName, column, "JSON", "SQL Server XML requires manual conversion before migrating to TiDB.");
            case "SQL_VARIANT", "HIERARCHYID", "GEOGRAPHY", "GEOMETRY" ->
                    unsupported(tableName, column, "TEXT", "SQL Server proprietary or spatial types need manual remodeling before migrating to TiDB.");
            default -> mapGenericColumn(tableName, column, typeName);
        };
    }

    private ColumnMappingResult mapPostgreSqlColumn(String tableName, SourceColumnDefinition column, String typeName) {
        return switch (typeName) {
            case "BOOL", "BOOLEAN" -> new ColumnMappingResult("BOOLEAN", null);
            case "INT2", "SMALLINT" -> new ColumnMappingResult("SMALLINT", null);
            case "INT4", "INTEGER", "SERIAL" -> new ColumnMappingResult("INT", null);
            case "INT8", "BIGINT", "BIGSERIAL" -> new ColumnMappingResult("BIGINT", null);
            case "NUMERIC", "DECIMAL" -> new ColumnMappingResult(decimalTargetType(column.columnSize(), column.decimalDigits(), 65, 30), null);
            case "FLOAT4", "REAL" -> new ColumnMappingResult("FLOAT", null);
            case "FLOAT8", "DOUBLE PRECISION" -> new ColumnMappingResult("DOUBLE", null);
            case "CHAR", "BPCHAR" -> new ColumnMappingResult("CHAR(" + safeLength(column.columnSize(), 1, 255) + ")", null);
            case "VARCHAR" -> new ColumnMappingResult(varcharOrText(column.columnSize()), null);
            case "TEXT" -> new ColumnMappingResult("LONGTEXT", null);
            case "JSON", "JSONB" -> new ColumnMappingResult("JSON", null);
            case "UUID" -> new ColumnMappingResult("CHAR(36)", null);
            case "BYTEA" -> new ColumnMappingResult("LONGBLOB", null);
            case "DATE" -> new ColumnMappingResult("DATE", null);
            case "TIME", "TIMETZ", "TIME WITH TIME ZONE" ->
                    unsupported(tableName, column, "VARCHAR(32)", "PostgreSQL time with time zone needs manual review because TiDB TIME does not preserve zone semantics.");
            case "TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE" ->
                    unsupported(tableName, column, "VARCHAR(64)", "PostgreSQL timestamp with time zone is stored in UTC and rendered in session time zone; migrate with explicit review to avoid timezone semantic loss.");
            case "TIMESTAMP", "TIMESTAMP WITHOUT TIME ZONE" -> new ColumnMappingResult("DATETIME(6)", null);
            case "INTERVAL" -> unsupported(tableName, column, "VARCHAR(64)", "PostgreSQL INTERVAL should be normalized before migrating to TiDB.");
            case "_TEXT", "_VARCHAR", "ARRAY" -> unsupported(tableName, column, "JSON", "PostgreSQL array types are not directly compatible with TiDB relational types.");
            default -> mapGenericColumn(tableName, column, typeName);
        };
    }

    private ColumnMappingResult mapGenericColumn(String tableName, SourceColumnDefinition column, String typeName) {
        return switch (column.dataType()) {
            case Types.BOOLEAN -> new ColumnMappingResult("BOOLEAN", null);
            case Types.BIT -> new ColumnMappingResult(column.columnSize() <= 1 ? "BOOLEAN" : "BIT(" + safeLength(column.columnSize(), 1, 64) + ")", null);
            case Types.TINYINT -> new ColumnMappingResult("TINYINT", null);
            case Types.SMALLINT -> new ColumnMappingResult("SMALLINT", null);
            case Types.INTEGER -> new ColumnMappingResult("INT", null);
            case Types.BIGINT -> new ColumnMappingResult("BIGINT", null);
            case Types.FLOAT, Types.REAL -> new ColumnMappingResult("FLOAT", null);
            case Types.DOUBLE -> new ColumnMappingResult("DOUBLE", null);
            case Types.NUMERIC, Types.DECIMAL -> new ColumnMappingResult(decimalTargetType(column.columnSize(), column.decimalDigits(), 65, 30), null);
            case Types.CHAR, Types.NCHAR -> new ColumnMappingResult("CHAR(" + safeLength(column.columnSize(), 1, 255) + ")", null);
            case Types.VARCHAR, Types.NVARCHAR -> new ColumnMappingResult(varcharOrText(column.columnSize()), null);
            case Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> new ColumnMappingResult("LONGTEXT", null);
            case Types.DATE -> new ColumnMappingResult("DATE", null);
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> new ColumnMappingResult(temporalWithPrecision("TIME", column.decimalDigits()), null);
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> new ColumnMappingResult("DATETIME(6)", null);
            case Types.BINARY -> new ColumnMappingResult("BINARY(" + safeLength(column.columnSize(), 1, 255) + ")", null);
            case Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> new ColumnMappingResult("LONGBLOB", null);
            default -> {
                if ("JSON".equals(typeName) || "JSONB".equals(typeName)) {
                    yield new ColumnMappingResult("JSON", null);
                }
                if ("UUID".equals(typeName) || "UNIQUEIDENTIFIER".equals(typeName)) {
                    yield new ColumnMappingResult("CHAR(36)", null);
                }
                if ("BYTEA".equals(typeName)) {
                    yield new ColumnMappingResult("LONGBLOB", null);
                }
                yield unsupported(tableName, column, "TEXT", "Source type " + typeName + " is not mapped automatically to TiDB.");
            }
        };
    }

    private ColumnMappingResult unsupported(String tableName, SourceColumnDefinition column, String suggestedTargetType, String reason) {
        return new ColumnMappingResult(
                suggestedTargetType,
                new UnsupportedTypeItemResponse(tableName, column.columnName(), column.typeName(), suggestedTargetType, reason)
        );
    }

    private String normalizedIntegerType(String typeName) {
        return "INTEGER".equals(typeName) ? "INT" : typeName;
    }

    private String decimalTargetType(int precision, int scale, int maxPrecision, int defaultPrecision) {
        int normalizedScale = Math.max(scale, 0);
        int normalizedPrecision = precision <= 0 ? defaultPrecision : precision;
        normalizedPrecision = Math.max(normalizedPrecision, normalizedScale);
        normalizedPrecision = Math.min(normalizedPrecision, maxPrecision);
        normalizedScale = Math.min(normalizedScale, 30);
        if (normalizedScale == 0) {
            return numericTargetType(normalizedPrecision, 0);
        }
        return "DECIMAL(" + normalizedPrecision + "," + normalizedScale + ")";
    }

    private String numericTargetType(int precision, int scale) {
        if (scale > 0) {
            int normalizedPrecision = precision <= 0 ? Math.max(scale + 8, 18) : precision;
            return "DECIMAL(" + normalizedPrecision + "," + scale + ")";
        }
        if (precision <= 0) {
            return "DECIMAL(38,0)";
        }
        if (precision <= 3) {
            return "TINYINT";
        }
        if (precision <= 5) {
            return "SMALLINT";
        }
        if (precision <= 9) {
            return "INT";
        }
        if (precision <= 18) {
            return "BIGINT";
        }
        return "DECIMAL(" + precision + ",0)";
    }

    private String temporalWithPrecision(String baseType, int scale) {
        int normalizedScale = Math.max(0, Math.min(scale, 6));
        return normalizedScale == 0 ? baseType : baseType + "(" + normalizedScale + ")";
    }

    private int safeLength(int value, int minimum, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(value, minimum);
    }

    private String varcharOrText(int length) {
        if (length <= 0) {
            return "TEXT";
        }
        return length > 16383 ? "TEXT" : "VARCHAR(" + length + ")";
    }

    private String varbinaryOrBlob(int length) {
        if (length <= 0) {
            return "BLOB";
        }
        return length > 16383 ? "BLOB" : "VARBINARY(" + length + ")";
    }

    private String normalizedType(String typeName) {
        return typeName == null ? "" : typeName.toUpperCase(Locale.ROOT).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record SourceColumnDefinition(
            String columnName,
            String typeName,
            int dataType,
            int columnSize,
            int decimalDigits,
            boolean nullable
    ) {
    }

    public record ColumnMappingResult(
            String targetType,
            UnsupportedTypeItemResponse unsupportedItem
    ) {
    }
}
