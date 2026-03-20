package com.example.sync.admin.dto;

import com.example.sync.core.config.SyncJobDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SyncJobUpsertRequest(
        @NotBlank String name,
        @Valid @NotNull SyncJobDefinition definition
) {
}
