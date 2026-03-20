package com.example.sync.core.runtime;

import com.example.sync.core.model.ChangeOperation;
import java.time.Instant;
import java.util.Map;

public record StandardChangeEvent(
        String sourceCatalog,
        String sourceSchema,
        String sourceTable,
        Map<String, Object> keyValues,
        Map<String, Object> beforeValues,
        Map<String, Object> afterValues,
        ChangeOperation operation,
        Instant eventTime,
        Instant processedTime
) {
}
