package com.example.sync.core.runtime;

import com.example.sync.core.model.JobPhase;

public record TaskProgressSnapshot(
        JobPhase phase,
        int percent,
        String message,
        Long lagMillis,
        String latestCatalog,
        String latestSchema,
        String latestTable,
        String latestPrimaryKey
) {
}
