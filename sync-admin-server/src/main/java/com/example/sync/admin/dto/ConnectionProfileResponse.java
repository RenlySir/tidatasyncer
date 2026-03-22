package com.example.sync.admin.dto;

import com.example.sync.admin.domain.ConnectionProfileRole;
import com.example.sync.admin.domain.DatabaseEndpointType;
import java.time.Instant;

public record ConnectionProfileResponse(
        Long id,
        String name,
        ConnectionProfileRole role,
        DatabaseEndpointType databaseType,
        String host,
        Integer port,
        String databaseName,
        String schemaName,
        String username,
        String password,
        String jdbcUrl,
        String jdbcParameters,
        String csvDirectory,
        String permissionNote,
        Integer tidbStatusPort,
        Instant createdAt,
        Instant updatedAt
) {
}
