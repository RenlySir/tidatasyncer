package com.example.sync.admin.dto;

import java.time.Instant;

public record SyncJobLogResponse(
        Long id,
        Long jobId,
        String level,
        String message,
        Instant createdAt
) {
}
