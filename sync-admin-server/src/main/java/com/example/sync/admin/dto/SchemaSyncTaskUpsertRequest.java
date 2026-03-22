package com.example.sync.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record SchemaSyncTaskUpsertRequest(
        @NotBlank String name,
        @NotNull Long sourceProfileId,
        @NotNull Long targetProfileId,
        @NotBlank String tableSelectionMode,
        List<String> selectedTables,
        Map<String, String> overrideMappings
) {
}
