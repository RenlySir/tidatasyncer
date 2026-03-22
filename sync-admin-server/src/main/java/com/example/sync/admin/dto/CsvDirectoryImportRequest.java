package com.example.sync.admin.dto;

import com.example.sync.core.config.TargetConnectionProperties;
import com.example.sync.core.model.DeploymentArchitecture;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CsvDirectoryImportRequest(
        @NotBlank String directoryPath,
        @NotNull DeploymentArchitecture deploymentArchitecture,
        @Valid @NotNull TargetConnectionProperties target
) {
}
