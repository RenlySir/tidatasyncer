package com.example.sync.core.config;

import com.example.sync.core.model.SourceDatabaseType;

public record SourceConnectionProperties(
        SourceDatabaseType databaseType,
        String host,
        Integer port,
        String databaseName,
        String schemaName,
        String username,
        String password,
        String jdbcUrl,
        String commandTemplate
) {
}
