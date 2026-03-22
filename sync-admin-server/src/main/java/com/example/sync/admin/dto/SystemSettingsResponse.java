package com.example.sync.admin.dto;

import com.example.sync.core.model.DeploymentArchitecture;
import java.time.Instant;

public record SystemSettingsResponse(
        DeploymentArchitecture deploymentArchitecture,
        Instant updatedAt
) {
}
