package com.example.sync.admin.dto;

import com.example.sync.admin.domain.SyncJobStatus;
import com.example.sync.core.model.JobPhase;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.model.SyncMode;
import java.time.Instant;

public record SyncJobResponse(
        Long id,
        String name,
        SourceDatabaseType sourceDatabaseType,
        SyncMode syncMode,
        SyncJobStatus status,
        JobPhase phase,
        Integer progressPercent,
        String lastMessage,
        String lastError,
        Long lastLagMillis,
        Integer exportedTableCount,
        Integer totalTableCount,
        Long exportedBytes,
        Integer importedTableCount,
        Long importedBytes,
        String latestLogPosition,
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
