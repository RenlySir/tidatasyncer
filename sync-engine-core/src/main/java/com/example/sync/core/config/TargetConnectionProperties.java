package com.example.sync.core.config;

public record TargetConnectionProperties(
        String host,
        Integer port,
        String databaseName,
        String username,
        String password,
        String jdbcUrl,
        String lightningBinary
) {
}
