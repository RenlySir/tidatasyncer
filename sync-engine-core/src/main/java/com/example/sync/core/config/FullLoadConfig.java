package com.example.sync.core.config;

import java.util.Map;

public record FullLoadConfig(
        String exportToolBinary,
        String exportBaseDir,
        Integer fetchSize,
        Integer parallelism,
        Map<String, String> additionalProperties
) {
}
