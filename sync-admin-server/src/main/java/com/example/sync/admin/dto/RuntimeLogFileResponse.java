package com.example.sync.admin.dto;

import java.time.Instant;

public record RuntimeLogFileResponse(
        String key,
        String displayName,
        String absolutePath,
        boolean exists,
        long sizeBytes,
        Instant lastModifiedAt
) {
}
