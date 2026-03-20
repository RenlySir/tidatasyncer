package com.example.sync.admin.controller;

import com.example.sync.admin.domain.SyncJobStatus;
import com.example.sync.admin.dto.DashboardOverviewResponse;
import com.example.sync.admin.service.SyncJobService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final SyncJobService jobService;

    public DashboardController(SyncJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/overview")
    public DashboardOverviewResponse overview() {
        List<com.example.sync.admin.dto.SyncJobResponse> jobs = jobService.list();
        return new DashboardOverviewResponse(
                jobs.size(),
                jobs.stream().filter(job -> job.status() == SyncJobStatus.RUNNING).count(),
                jobs.stream().filter(job -> job.status() == SyncJobStatus.FAILED).count(),
                jobs.stream().filter(job -> job.status() == SyncJobStatus.COMPLETED).count(),
                jobs.stream().sorted((a, b) -> b.updatedAt().compareTo(a.updatedAt())).limit(10).toList()
        );
    }
}
