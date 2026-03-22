package com.example.sync.admin.dto;

import java.time.Instant;
import java.util.List;

public record RuntimeLogTailResponse(
        String key,
        String displayName,
        String absolutePath,
        boolean exists,
        long sizeBytes,
        Instant lastModifiedAt,
        int lineCount,
        List<String> lines
) {
}
