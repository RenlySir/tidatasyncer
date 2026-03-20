package com.example.sync.admin.service;

import com.example.sync.admin.domain.SyncJobEntity;
import com.example.sync.admin.domain.SyncJobLogEntity;
import com.example.sync.admin.repository.SyncJobLogRepository;
import com.example.sync.admin.repository.SyncJobRepository;
import com.example.sync.core.model.JobPhase;
import com.example.sync.core.runtime.StandardChangeEvent;
import com.example.sync.core.spi.ProgressReporter;
import java.util.concurrent.atomic.AtomicBoolean;

public class JobProgressReporterImpl implements ProgressReporter {

    private final Long jobId;
    private final SyncJobRepository jobRepository;
    private final SyncJobLogRepository logRepository;
    private final AtomicBoolean stopRequested;

    public JobProgressReporterImpl(
            Long jobId,
            SyncJobRepository jobRepository,
            SyncJobLogRepository logRepository,
            AtomicBoolean stopRequested
    ) {
        this.jobId = jobId;
        this.jobRepository = jobRepository;
        this.logRepository = logRepository;
        this.stopRequested = stopRequested;
    }

    @Override
    public void updatePhase(JobPhase phase, int percent, String message) {
        SyncJobEntity entity = getJob();
        entity.setPhase(phase);
        entity.setProgressPercent(percent);
        entity.setLastMessage(message);
        jobRepository.save(entity);
        log("INFO", message);
    }

    @Override
    public void updateLag(long lagMillis, String message) {
        SyncJobEntity entity = getJob();
        entity.setLastLagMillis(lagMillis);
        entity.setLastMessage(message);
        jobRepository.save(entity);
    }

    @Override
    public void updateLatestEvent(StandardChangeEvent event) {
        SyncJobEntity entity = getJob();
        entity.setLatestCatalog(event.sourceCatalog());
        entity.setLatestSchema(event.sourceSchema());
        entity.setLatestTable(event.sourceTable());
        entity.setLatestPrimaryKey(event.keyValues().toString());
        jobRepository.save(entity);
    }

    @Override
    public void log(String level, String message) {
        SyncJobLogEntity logEntity = new SyncJobLogEntity();
        logEntity.setJobId(jobId);
        logEntity.setLevel(level);
        logEntity.setMessage(message);
        logRepository.save(logEntity);
    }

    @Override
    public boolean isStopRequested() {
        return stopRequested.get();
    }

    private SyncJobEntity getJob() {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }
}
