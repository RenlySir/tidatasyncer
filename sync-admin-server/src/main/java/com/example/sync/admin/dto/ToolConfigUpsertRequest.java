package com.example.sync.admin.dto;

import com.example.sync.admin.domain.DatabaseEndpointType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ToolConfigUpsertRequest(
        @NotBlank String name,
        @NotNull DatabaseEndpointType databaseType,
        String exportToolBinary,
        String lightningBinary,
        String notes
) {
}
