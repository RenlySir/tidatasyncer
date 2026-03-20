package com.example.sync.admin.service;

import com.example.sync.admin.domain.SyncJobEntity;
import com.example.sync.admin.domain.SyncJobStatus;
import com.example.sync.admin.repository.SyncJobLogRepository;
import com.example.sync.admin.repository.SyncJobRepository;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.JobPhase;
import com.example.sync.core.model.SyncMode;
import com.example.sync.core.spi.ChangeCaptureHandle;
import com.example.sync.core.spi.ChangeEventSink;
import com.example.sync.core.spi.ChangeEventSource;
import com.example.sync.core.spi.FullLoadExporter;
import com.example.sync.core.spi.FullLoadImporter;
import com.example.sync.core.spi.ProgressReporter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class SyncJobRuntimeManager {

    private final SyncJobRepository jobRepository;
    private final SyncJobLogRepository logRepository;
    private final SyncJobMapper mapper;
    private final List<FullLoadExporter> exporters;
    private final FullLoadImporter importer;
    private final List<ChangeEventSource> sources;
    private final ObjectProvider<ChangeEventSink> sinkProvider;
    private final ThreadPoolTaskExecutor jobExecutor;
    private final Map<Long, JobRuntimeContext> runtimes = new ConcurrentHashMap<>();

    public SyncJobRuntimeManager(
            SyncJobRepository jobRepository,
            SyncJobLogRepository logRepository,
            SyncJobMapper mapper,
            List<FullLoadExporter> exporters,
            FullLoadImporter importer,
            List<ChangeEventSource> sources,
            ObjectProvider<ChangeEventSink> sinkProvider,
            ThreadPoolTaskExecutor jobExecutor
    ) {
        this.jobRepository = jobRepository;
        this.logRepository = logRepository;
        this.mapper = mapper;
        this.exporters = exporters;
        this.importer = importer;
        this.sources = sources;
        this.sinkProvider = sinkProvider;
        this.jobExecutor = jobExecutor;
    }

    public void start(Long jobId) {
        if (runtimes.containsKey(jobId)) {
            throw new IllegalStateException("Job is already running: " + jobId);
        }

        SyncJobEntity entity = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        SyncJobDefinition persistedDefinition = mapper.toDefinition(entity);
        SyncJobDefinition definition = new SyncJobDefinition(
                entity.getId(),
                entity.getName(),
                persistedDefinition.syncMode(),
                persistedDefinition.source(),
                persistedDefinition.target(),
                persistedDefinition.tableMappings(),
                persistedDefinition.fullLoad(),
                persistedDefinition.incremental()
        );
        JobRuntimeContext context = new JobRuntimeContext();
        runtimes.put(jobId, context);

        entity.setStatus(SyncJobStatus.RUNNING);
        entity.setPhase(JobPhase.VALIDATING);
        entity.setProgressPercent(0);
        entity.setLastError(null);
        entity.setStartedAt(Instant.now());
        entity.setStoppedAt(null);
        jobRepository.save(entity);

        ProgressReporter reporter = new JobProgressReporterImpl(jobId, jobRepository, logRepository, context.getStopRequested());
        context.setFuture(jobExecutor.submit(() -> executeJob(entity, definition, reporter, context)));
    }

    public void stop(Long jobId) {
        JobRuntimeContext context = runtimes.get(jobId);
        if (context == null) {
            return;
        }
        context.getStopRequested().set(true);
        try {
            if (context.getCaptureHandle() != null) {
                context.getCaptureHandle().close();
            }
            if (context.getFuture() != null) {
                context.getFuture().cancel(true);
            }
        } catch (Exception ignored) {
        }

        SyncJobEntity entity = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        entity.setStatus(SyncJobStatus.STOPPED);
        entity.setPhase(JobPhase.STOPPED);
        entity.setStoppedAt(Instant.now());
        entity.setLastMessage("Job stopped");
        jobRepository.save(entity);
        runtimes.remove(jobId);
    }

    private void executeJob(SyncJobEntity entity, SyncJobDefinition definition, ProgressReporter reporter, JobRuntimeContext context) {
        try {
            validate(definition, reporter);
            switch (definition.syncMode()) {
                case FULL_ONLY -> runFullOnly(definition, reporter);
                case INCREMENTAL_ONLY -> runIncrementalOnly(definition, reporter, context);
                case FULL_AND_INCREMENTAL -> runFullAndIncremental(definition, reporter, context);
            }
            if (!context.getStopRequested().get() && definition.syncMode() == SyncMode.FULL_ONLY) {
                markCompleted(entity.getId(), "Full load completed");
            }
        } catch (Exception ex) {
            markFailed(entity.getId(), ex);
        } finally {
            if (definition.syncMode() == SyncMode.FULL_ONLY || context.getStopRequested().get()) {
                runtimes.remove(entity.getId());
            }
        }
    }

    private void validate(SyncJobDefinition definition, ProgressReporter reporter) {
        reporter.updatePhase(JobPhase.VALIDATING, 5, "Validating job definition");
        if (definition.tableMappings() == null || definition.tableMappings().isEmpty()) {
            throw new IllegalArgumentException("At least one table mapping is required");
        }
        reporter.updatePhase(JobPhase.VALIDATING, 100, "Validation finished");
    }

    private void runFullOnly(SyncJobDefinition definition, ProgressReporter reporter) throws Exception {
        FullLoadExporter exporter = selectExporter(definition);
        var exportResult = exporter.export(definition, reporter);
        importer.importCsv(definition, exportResult, reporter);
    }

    private void runIncrementalOnly(SyncJobDefinition definition, ProgressReporter reporter, JobRuntimeContext context) throws Exception {
        ChangeEventSink sink = sinkProvider.getObject();
        sink.open(definition, reporter);
        try {
            ChangeCaptureHandle handle = selectSource(definition).start(definition, sink, reporter);
            context.setCaptureHandle(handle);
            handle.awaitStop();
            if (!context.getStopRequested().get()) {
                markCompleted(definition.jobId(), "Incremental sync stopped normally");
            }
        } finally {
            sink.close();
        }
    }

    private void runFullAndIncremental(SyncJobDefinition definition, ProgressReporter reporter, JobRuntimeContext context) throws Exception {
        ChangeEventSink sink = sinkProvider.getObject();
        BufferingChangeEventSink bufferingSink = new BufferingChangeEventSink(sink);
        bufferingSink.open(definition, reporter);
        try {
            reporter.updatePhase(JobPhase.BUFFERING_INCREMENTAL, 10, "Starting CDC buffering");
            ChangeCaptureHandle handle = selectSource(definition).start(definition, bufferingSink, reporter);
            context.setCaptureHandle(handle);

            FullLoadExporter exporter = selectExporter(definition);
            var exportResult = exporter.export(definition, reporter);
            importer.importCsv(definition, exportResult, reporter);

            reporter.updatePhase(JobPhase.REPLAYING_INCREMENTAL_BUFFER, 95, "Replaying buffered incremental events");
            bufferingSink.replayAndSwitch(reporter);
            reporter.updatePhase(JobPhase.RUNNING_INCREMENTAL, 100, "Full load finished, incremental sync is running");
            handle.awaitStop();
            if (!context.getStopRequested().get()) {
                markCompleted(definition.jobId(), "Hybrid sync stopped normally");
            }
        } finally {
            bufferingSink.close();
        }
    }

    private FullLoadExporter selectExporter(SyncJobDefinition definition) {
        return exporters.stream()
                .filter(exporter -> exporter.supports(definition))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No exporter found for " + definition.source().databaseType()));
    }

    private ChangeEventSource selectSource(SyncJobDefinition definition) {
        return sources.stream()
                .filter(source -> source.supports(definition))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No incremental source found for " + definition.source().databaseType()));
    }

    private void markCompleted(Long jobId, String message) {
        SyncJobEntity entity = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        entity.setStatus(SyncJobStatus.COMPLETED);
        entity.setPhase(JobPhase.COMPLETED);
        entity.setProgressPercent(100);
        entity.setLastMessage(message);
        entity.setStoppedAt(Instant.now());
        jobRepository.save(entity);
        runtimes.remove(jobId);
    }

    private void markFailed(Long jobId, Exception ex) {
        SyncJobEntity entity = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        entity.setStatus(SyncJobStatus.FAILED);
        entity.setPhase(JobPhase.FAILED);
        entity.setLastError(ex.getMessage());
        entity.setStoppedAt(Instant.now());
        jobRepository.save(entity);
        JobProgressReporterImpl reporter = new JobProgressReporterImpl(jobId, jobRepository, logRepository, new java.util.concurrent.atomic.AtomicBoolean(false));
        reporter.log("ERROR", ex.getMessage());
        runtimes.remove(jobId);
    }
}
