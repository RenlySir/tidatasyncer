package com.example.sync.connectors.util;

import com.example.sync.core.config.SourceConnectionProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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

        Map<String, String> mergedProperties = new LinkedHashMap<>(parseQueryParameters(source.jdbcParameters()));
        if (extraProperties != null) {
            extraProperties.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    mergedProperties.putIfAbsent(key, value);
                }
            });
        }

        String authSource = mergedProperties.get("authSource");
        String replicaSet = mergedProperties.get("replicaSet");
        boolean hasQuery = false;
        if (authSource != null && !authSource.isBlank()) {
            builder.append("?authSource=").append(urlEncode(authSource));
            hasQuery = true;
        }
        if (replicaSet != null && !replicaSet.isBlank()) {
            builder.append(hasQuery ? "&" : "?")
                    .append("replicaSet=").append(urlEncode(replicaSet));
            hasQuery = true;
        }
        for (Map.Entry<String, String> entry : mergedProperties.entrySet()) {
            if ("authSource".equals(entry.getKey()) || "replicaSet".equals(entry.getKey())) {
                continue;
            }
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            builder.append(hasQuery ? "&" : "?")
                    .append(urlEncode(entry.getKey()))
                    .append("=")
                    .append(urlEncode(entry.getValue()));
            hasQuery = true;
        }
        return builder.toString();
    }

    public static Map<String, String> parseQueryParameters(String parameters) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (parameters == null || parameters.isBlank()) {
            return parsed;
        }
        String normalized = parameters.trim();
        while (normalized.startsWith("?") || normalized.startsWith("&")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            return parsed;
        }
        for (String part : normalized.split("&")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] keyValue = trimmed.split("=", 2);
            parsed.put(keyValue[0], keyValue.length > 1 ? keyValue[1] : "");
        }
        return parsed;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
