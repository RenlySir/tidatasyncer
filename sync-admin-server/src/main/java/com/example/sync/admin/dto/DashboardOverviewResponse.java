package com.example.sync.admin.dto;

import java.util.List;

public record DashboardOverviewResponse(
        long totalJobs,
        long runningJobs,
        long failedJobs,
        long completedJobs,
        List<SyncJobResponse> recentJobs
) {
}
