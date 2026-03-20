package com.example.sync.connectors.util;

import com.example.sync.core.config.SourceConnectionProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class MongoConnectionSupport {

    private MongoConnectionSupport() {
    }

    public static String resolveConnectionString(SourceConnectionProperties source, Map<String, String> extraProperties) {
        if (source.jdbcUrl() != null && !source.jdbcUrl().isBlank()) {
            return source.jdbcUrl();
        }

        StringBuilder builder = new StringBuilder("mongodb://");
        if (source.username() != null && !source.username().isBlank()) {
            builder.append(urlEncode(source.username()));
            if (source.password() != null && !source.password().isBlank()) {
                builder.append(':').append(urlEncode(source.password()));
            }
            builder.append('@');
        }
        builder.append(source.host());
        if (source.port() != null) {
            builder.append(':').append(source.port());
        }
        builder.append('/');
        if (source.databaseName() != null) {
            builder.append(source.databaseName());
        }

        String authSource = extraProperties == null ? null : extraProperties.get("authSource");
        String replicaSet = extraProperties == null ? null : extraProperties.get("replicaSet");
        boolean hasQuery = false;
        if (authSource != null && !authSource.isBlank()) {
            builder.append("?authSource=").append(urlEncode(authSource));
            hasQuery = true;
        }
        if (replicaSet != null && !replicaSet.isBlank()) {
            builder.append(hasQuery ? "&" : "?")
                    .append("replicaSet=").append(urlEncode(replicaSet));
        }
        return builder.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
