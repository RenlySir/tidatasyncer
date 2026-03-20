package com.example.sync.admin.dto;

import com.example.sync.admin.domain.SyncJobStatus;
import com.example.sync.core.model.JobPhase;
import com.example.sync.core.model.SyncMode;
import java.time.Instant;

public record SyncJobResponse(
        Long id,
        String name,
        SyncMode syncMode,
        SyncJobStatus status,
        JobPhase phase,
        Integer progressPercent,
        String lastMessage,
        String lastError,
        Long lastLagMillis,
        String latestCatalog,
        String latestSchema,
        String latestTable,
        String latestPrimaryKey,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant stoppedAt
) {
}
