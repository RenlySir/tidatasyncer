package com.example.sync.admin.dto;

import com.example.sync.core.config.SyncJobDefinition;

public record SyncJobDefinitionResponse(
        Long jobId,
        String jobName,
        SyncJobDefinition definition
) {
}
