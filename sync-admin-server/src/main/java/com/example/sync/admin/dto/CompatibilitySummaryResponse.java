package com.example.sync.admin.dto;

public record CompatibilitySummaryResponse(
        int totalFindings,
        int incompatibleCount,
        int partialCount,
        int compatibleCount,
        int errorCount,
        int warningCount,
        int infoCount,
        int tableCount,
        int viewCount,
        int triggerCount,
        int procedureCount,
        int functionCount,
        int sequenceCount
) {
}
