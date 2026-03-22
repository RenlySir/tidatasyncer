package com.example.sync.core.config;

public record TargetConnectionProperties(
        String host,
        Integer port,
        String databaseName,
        String username,
        String password,
        String jdbcUrl,
        String jdbcParameters,
        String lightningBinary,
        Integer statusPort
) {

    public TargetConnectionProperties(
            String host,
            Integer port,
            String databaseName,
            String username,
            String password,
            String jdbcUrl,
            String jdbcParameters,
            String lightningBinary
    ) {
        this(host, port, databaseName, username, password, jdbcUrl, jdbcParameters, lightningBinary, 10080);
    }
}
