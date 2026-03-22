package com.example.sync.admin.dto;

import com.example.sync.admin.domain.SchemaSyncTaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SchemaSyncTaskResponse(
        Long id,
        String name,
        Long sourceProfileId,
        Long targetProfileId,
        String tableSelectionMode,
        List<String> selectedTables,
        Map<String, String> overrideMappings,
        SchemaSyncTaskStatus status,
        String lastMessage,
        String generatedDdl,
        String generatedDdlPath,
        String unsupportedItemsPath,
        List<UnsupportedTypeItemResponse> unsupportedItems,
        Instant executedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
