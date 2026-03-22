package com.example.sync.admin.dto;

import com.example.sync.admin.domain.DatabaseEndpointType;
import java.time.Instant;

public record ToolConfigResponse(
        Long id,
        String name,
        DatabaseEndpointType databaseType,
        String exportToolBinary,
        String lightningBinary,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
