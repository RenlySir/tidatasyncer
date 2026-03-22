package com.example.sync.admin.dto;

import com.example.sync.admin.domain.ConnectionProfileRole;
import com.example.sync.admin.domain.DatabaseEndpointType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConnectionProfileUpsertRequest(
        @NotBlank String name,
        @NotNull ConnectionProfileRole role,
        @NotNull DatabaseEndpointType databaseType,
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
        Integer tidbStatusPort
) {
}
