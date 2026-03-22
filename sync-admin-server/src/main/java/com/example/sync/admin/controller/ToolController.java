package com.example.sync.admin.controller;

import com.example.sync.admin.dto.ManagedToolPathsResponse;
import com.example.sync.admin.service.SystemSettingsService;
import com.example.sync.connectors.util.ProjectManagedToolResolver;
import com.example.sync.core.model.DeploymentArchitecture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final SystemSettingsService systemSettingsService;

    public ToolController(SystemSettingsService systemSettingsService) {
        this.systemSettingsService = systemSettingsService;
    }

    @GetMapping("/managed-paths")
    public ManagedToolPathsResponse managedPaths(
            @RequestParam(value = "architecture", required = false) DeploymentArchitecture architecture
    ) {
        DeploymentArchitecture resolvedArchitecture = architecture == null
                ? systemSettingsService.getDeploymentArchitecture()
                : architecture;
        return new ManagedToolPathsResponse(
                ProjectManagedToolResolver.managedToolBinary("tidb-lightning", resolvedArchitecture).toAbsolutePath().toString(),
                ProjectManagedToolResolver.managedToolBinary("dumpling", resolvedArchitecture).toAbsolutePath().toString(),
                ProjectManagedToolResolver.managedToolBinary("sqluldr2", resolvedArchitecture).toAbsolutePath().toString(),
                ProjectManagedToolResolver.managedToolBinary("bcp", resolvedArchitecture).toAbsolutePath().toString(),
                ProjectManagedToolResolver.managedToolBinary("sqlcmd", resolvedArchitecture).toAbsolutePath().toString()
        );
    }
}
