package com.example.sync.core.config;

import java.util.Map;

public record IncrementalConfig(
        String serverName,
        String slotName,
        String publicationName,
        String offsetStoragePath,
        Integer pollingIntervalSeconds,
        Integer batchSize,
        Map<String, String> additionalProperties
) {
}
