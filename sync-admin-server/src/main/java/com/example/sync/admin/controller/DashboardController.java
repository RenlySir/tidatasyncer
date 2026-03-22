package com.example.sync.admin.controller;

import com.example.sync.admin.domain.CompatibilityReportStatus;
import com.example.sync.admin.domain.ConnectionProfileRole;
import com.example.sync.admin.domain.DatabaseEndpointType;
import com.example.sync.admin.domain.SchemaSyncTaskStatus;
import com.example.sync.admin.domain.SyncJobStatus;
import com.example.sync.admin.dto.CompatibilityReportResponse;
import com.example.sync.admin.dto.ConnectionProfileResponse;
import com.example.sync.admin.dto.DashboardOverviewResponse;
import com.example.sync.admin.dto.SchemaSyncTaskResponse;
import com.example.sync.admin.dto.SyncJobResponse;
import com.example.sync.admin.service.CompatibilityReportService;
import com.example.sync.admin.service.ConnectionProfileService;
import com.example.sync.admin.service.SchemaSyncTaskService;
import com.example.sync.admin.service.SyncJobService;
import com.example.sync.admin.service.ToolConfigService;
import com.example.sync.core.model.SyncMode;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ConnectionProfileService connectionProfileService;
    private final ToolConfigService toolConfigService;
    private final CompatibilityReportService compatibilityReportService;
    private final SchemaSyncTaskService schemaSyncTaskService;
    private final SyncJobService jobService;

    public DashboardController(
            ConnectionProfileService connectionProfileService,
            ToolConfigService toolConfigService,
            CompatibilityReportService compatibilityReportService,
            SchemaSyncTaskService schemaSyncTaskService,
            SyncJobService jobService
    ) {
        this.connectionProfileService = connectionProfileService;
        this.toolConfigService = toolConfigService;
        this.compatibilityReportService = compatibilityReportService;
        this.schemaSyncTaskService = schemaSyncTaskService;
        this.jobService = jobService;
    }

    @GetMapping("/overview")
    public DashboardOverviewResponse overview() {
        List<ConnectionProfileResponse> sourceProfiles = connectionProfileService.list(ConnectionProfileRole.SOURCE);
        List<ConnectionProfileResponse> targetProfiles = connectionProfileService.list(ConnectionProfileRole.TARGET);
        List<CompatibilityReportResponse> compatibilityReports = compatibilityReportService.list();
        List<SchemaSyncTaskResponse> schemaTasks = schemaSyncTaskService.list();
        List<SyncJobResponse> jobs = jobService.list();
        long sourceProfileCount = sourceProfiles.size();
        long targetProfileCount = targetProfiles.size();
        long csvSourceCount = sourceProfiles.stream()
                .filter(profile -> profile.databaseType() == DatabaseEndpointType.CSV)
                .count();
        long toolConfigCount = toolConfigService.list().size();
        long compatibilityReportCount = compatibilityReports.size();
        long completedCompatibilityReportCount = compatibilityReports.stream()
                .filter(report -> report.status() == CompatibilityReportStatus.COMPLETED)
                .count();
        long schemaTaskCount = schemaTasks.size();
        long completedSchemaTaskCount = schemaTasks.stream()
                .filter(task -> task.status() == SchemaSyncTaskStatus.COMPLETED)
                .count();
        long fullOnlyJobCount = jobs.stream().filter(job -> job.syncMode() == SyncMode.FULL_ONLY).count();
        long incrementalOnlyJobCount = jobs.stream().filter(job -> job.syncMode() == SyncMode.INCREMENTAL_ONLY).count();
        long fullAndIncrementalJobCount = jobs.stream().filter(job -> job.syncMode() == SyncMode.FULL_AND_INCREMENTAL).count();
        long batchEnabledJobCount = jobs.stream()
                .filter(job -> job.syncMode() == SyncMode.FULL_ONLY || job.syncMode() == SyncMode.FULL_AND_INCREMENTAL)
                .count();
        long realtimeEnabledJobCount = jobs.stream()
                .filter(job -> job.syncMode() == SyncMode.INCREMENTAL_ONLY || job.syncMode() == SyncMode.FULL_AND_INCREMENTAL)
                .count();

        return new DashboardOverviewResponse(
                jobs.size(),
                jobs.stream().filter(job -> job.status() == SyncJobStatus.RUNNING).count(),
                jobs.stream().filter(job -> job.status() == SyncJobStatus.FAILED).count(),
                jobs.stream().filter(job -> job.status() == SyncJobStatus.COMPLETED).count(),
                sourceProfileCount,
                targetProfileCount,
                csvSourceCount,
                toolConfigCount,
                compatibilityReportCount,
                completedCompatibilityReportCount,
                schemaTaskCount,
                completedSchemaTaskCount,
                batchEnabledJobCount,
                realtimeEnabledJobCount,
                fullOnlyJobCount,
                incrementalOnlyJobCount,
                fullAndIncrementalJobCount,
                pipelineReadinessScore(
                        sourceProfileCount,
                        targetProfileCount,
                        toolConfigCount,
                        completedCompatibilityReportCount,
                        completedSchemaTaskCount,
                        jobs.size()
                ),
                jobs.stream().sorted(Comparator.comparing(SyncJobResponse::updatedAt).reversed()).limit(10).toList()
        );
    }

    private int pipelineReadinessScore(
            long sourceProfileCount,
            long targetProfileCount,
            long toolConfigCount,
            long completedCompatibilityReportCount,
            long completedSchemaTaskCount,
            long totalJobs
    ) {
        int score = 0;
        if (sourceProfileCount > 0) {
            score += 20;
        }
        if (targetProfileCount > 0) {
            score += 20;
        }
        if (toolConfigCount > 0) {
            score += 20;
        }
        if (completedCompatibilityReportCount > 0) {
            score += 20;
        }
        if (completedSchemaTaskCount > 0) {
            score += 10;
        }
        if (totalJobs > 0) {
            score += 10;
        }
        return Math.min(score, 100);
    }
}
