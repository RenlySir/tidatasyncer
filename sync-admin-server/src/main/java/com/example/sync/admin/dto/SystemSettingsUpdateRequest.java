package com.example.sync.admin.dto;

import com.example.sync.core.model.DeploymentArchitecture;

public record SystemSettingsUpdateRequest(
        DeploymentArchitecture deploymentArchitecture
) {
}
