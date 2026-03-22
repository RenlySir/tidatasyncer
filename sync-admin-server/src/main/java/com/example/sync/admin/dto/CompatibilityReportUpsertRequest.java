package com.example.sync.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompatibilityReportUpsertRequest(
        @NotBlank String name,
        @NotNull Long sourceProfileId,
        @NotNull Long targetProfileId
) {
}
