package com.example.sync.admin.dto;

import java.util.List;

public record DashboardOverviewResponse(
        long totalJobs,
        long runningJobs,
        long failedJobs,
        long completedJobs,
        long sourceProfileCount,
        long targetProfileCount,
        long csvSourceCount,
        long toolConfigCount,
        long compatibilityReportCount,
        long completedCompatibilityReportCount,
        long schemaTaskCount,
        long completedSchemaTaskCount,
        long batchEnabledJobCount,
        long realtimeEnabledJobCount,
        long fullOnlyJobCount,
        long incrementalOnlyJobCount,
        long fullAndIncrementalJobCount,
        int pipelineReadinessScore,
        List<SyncJobResponse> recentJobs
) {
}
