package com.example.sync.connectors.util;

import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.TargetConnectionProperties;
import com.example.sync.core.model.SourceDatabaseType;

public final class JdbcConnectionSupport {

    private JdbcConnectionSupport() {
    }

    public static String resolveSourceJdbcUrl(SourceConnectionProperties source) {
        if (source.jdbcUrl() != null && !source.jdbcUrl().isBlank()) {
            return source.jdbcUrl();
        }
        return switch (source.databaseType()) {
            case CSV -> source.jdbcUrl();
            case MYSQL -> buildQueryStyleUrl("jdbc:mysql", source.host(), source.port(), source.databaseName(), source.jdbcParameters());
            case MARIADB -> buildQueryStyleUrl("jdbc:mariadb", source.host(), source.port(), source.databaseName(), source.jdbcParameters());
            case POSTGRESQL -> buildQueryStyleUrl("jdbc:postgresql", source.host(), source.port(), source.databaseName(), source.jdbcParameters());
            case ORACLE -> buildOracleUrl(source.host(), source.port(), source.databaseName(), source.jdbcParameters());
            case SQLSERVER -> buildSqlServerUrl(source.host(), source.port(), source.databaseName(), source.jdbcParameters());
            case DB2 -> buildDb2Url(source.host(), source.port(), source.databaseName(), source.jdbcParameters());
            case HANA -> buildHanaUrl(source.host(), source.port(), source.databaseName(), source.jdbcParameters());
            case MONGODB -> source.jdbcUrl();
        };
    }

    public static String resolveTargetJdbcUrl(TargetConnectionProperties target) {
        if (target.jdbcUrl() != null && !target.jdbcUrl().isBlank()) {
            return target.jdbcUrl();
        }
        return buildQueryStyleUrl("jdbc:mysql", target.host(), target.port(), target.databaseName(), target.jdbcParameters());
    }

    public static String previewSourceUrl(SourceConnectionProperties source) {
        if (source.databaseType() == SourceDatabaseType.MONGODB) {
            return MongoConnectionSupport.resolveConnectionString(source, MongoConnectionSupport.parseQueryParameters(source.jdbcParameters()));
        }
        return resolveSourceJdbcUrl(source);
    }

    private static String buildDb2Url(String host, Integer port, String databaseName, String parameters) {
        StringBuilder builder = new StringBuilder();
        builder.append("jdbc:db2://").append(nullToEmpty(host));
        if (port != null && port > 0) {
            builder.append(':').append(port);
        }
        builder.append('/').append(nullToEmpty(databaseName));
        String normalized = normalizeSemicolonParameters(parameters);
        if (normalized != null) {
            builder.append(':').append(normalized);
        }
        return builder.toString();
    }

    private static String buildQueryStyleUrl(String scheme, String host, Integer port, String databaseName, String parameters) {
        StringBuilder builder = new StringBuilder();
        builder.append(scheme).append("://").append(nullToEmpty(host));
        if (port != null && port > 0) {
            builder.append(':').append(port);
        }
        builder.append('/');
        builder.append(nullToEmpty(databaseName));
        appendQueryParameters(builder, parameters);
        return builder.toString();
    }

    private static String buildOracleUrl(String host, Integer port, String databaseName, String parameters) {
        StringBuilder builder = new StringBuilder();
        builder.append("jdbc:oracle:thin:@").append(nullToEmpty(host));
        if (port != null && port > 0) {
            builder.append(':').append(port);
        }
        if (databaseName != null && !databaseName.isBlank()) {
            builder.append('/').append(databaseName);
        }
        appendQueryParameters(builder, parameters);
        return builder.toString();
    }

    private static String buildSqlServerUrl(String host, Integer port, String databaseName, String parameters) {
        StringBuilder builder = new StringBuilder();
        builder.append("jdbc:sqlserver://").append(nullToEmpty(host));
        if (port != null && port > 0) {
            builder.append(':').append(port);
        }
        if (databaseName != null && !databaseName.isBlank()) {
            builder.append(";databaseName=").append(databaseName);
        }
        appendSemicolonParameters(builder, parameters);
        return builder.toString();
    }

    private static String buildHanaUrl(String host, Integer port, String databaseName, String parameters) {
        StringBuilder builder = new StringBuilder();
        builder.append("jdbc:sap://").append(nullToEmpty(host));
        if (port != null && port > 0) {
            builder.append(':').append(port);
        }
        boolean hasQuery = false;
        if (databaseName != null && !databaseName.isBlank()) {
            builder.append("/?databaseName=").append(databaseName);
            hasQuery = true;
        }
        appendQueryParameters(builder, parameters, hasQuery);
        return builder.toString();
    }

    private static void appendQueryParameters(StringBuilder builder, String parameters) {
        appendQueryParameters(builder, parameters, false);
    }

    private static void appendQueryParameters(StringBuilder builder, String parameters, boolean hasQuery) {
        String normalized = normalizeQueryParameters(parameters);
        if (normalized == null) {
            return;
        }
        builder.append(hasQuery ? '&' : '?').append(normalized);
    }

    private static void appendSemicolonParameters(StringBuilder builder, String parameters) {
        String normalized = normalizeSemicolonParameters(parameters);
        if (normalized == null) {
            return;
        }
        builder.append(';').append(normalized);
    }

    private static String normalizeQueryParameters(String parameters) {
        if (parameters == null) {
            return null;
        }
        String normalized = parameters.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        while (normalized.startsWith("?") || normalized.startsWith("&")) {
            normalized = normalized.substring(1);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeSemicolonParameters(String parameters) {
        if (parameters == null) {
            return null;
        }
        String normalized = parameters.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        while (normalized.startsWith(";")) {
            normalized = normalized.substring(1);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
