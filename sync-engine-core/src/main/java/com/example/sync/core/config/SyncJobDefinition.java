package com.example.sync.core.config;

import com.example.sync.core.model.DeploymentArchitecture;
import com.example.sync.core.model.SyncMode;
import java.util.List;

public record SyncJobDefinition(
        Long jobId,
        String jobName,
        SyncMode syncMode,
        DeploymentArchitecture deploymentArchitecture,
        SourceConnectionProperties source,
        TargetConnectionProperties target,
        List<TableMapping> tableMappings,
        FullLoadConfig fullLoad,
        IncrementalConfig incremental
) {
}
