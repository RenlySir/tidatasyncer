package com.example.sync.core.config;

import java.util.List;
import java.util.Map;

public record TableMapping(
        String sourceCatalog,
        String sourceSchema,
        String sourceTable,
        String targetDatabase,
        String targetTable,
        List<String> primaryKeys,
        String incrementalColumn,
        List<String> includedColumns,
        Map<String, String> columnMappings
) {
}
