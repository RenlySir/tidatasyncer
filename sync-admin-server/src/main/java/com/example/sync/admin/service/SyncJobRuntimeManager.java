package com.example.sync.admin.service;

import com.example.sync.admin.domain.SyncJobEntity;
import com.example.sync.admin.domain.SyncJobStatus;
import com.example.sync.connectors.util.JdbcConnectionSupport;
import com.example.sync.connectors.util.SourceTableMappingResolver;
import com.example.sync.admin.repository.SyncJobLogRepository;
import com.example.sync.admin.repository.SyncJobRepository;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.IncrementalConfig;
import com.example.sync.core.model.JobPhase;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.model.SyncMode;
import com.example.sync.core.spi.ChangeCaptureHandle;
import com.example.sync.core.spi.ChangeEventSink;
import com.example.sync.core.spi.ChangeEventSource;
import com.example.sync.core.spi.FullLoadExporter;
import com.example.sync.core.spi.FullLoadImporter;
import com.example.sync.core.spi.ProgressReporter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class SyncJobRuntimeManager {

    private static final ChangeEventSink NO_OP_SINK = new NoOpChangeEventSink();
    private static final Logger log = LoggerFactory.getLogger(SyncJobRuntimeManager.class);

    private final SyncJobRepository jobRepository;
    private final SyncJobLogRepository logRepository;
    private final SyncJobMapper mapper;
    private final List<FullLoadExporter> exporters;
    private final FullLoadImporter importer;
    private final List<ChangeEventSource> sources;
    private final ObjectProvider<ChangeEventSink> sinkProvider;
    private final ThreadPoolTaskExecutor jobExecutor;
    private final SystemSettingsService systemSettingsService;
    private final SourceTableMappingResolver tableMappingResolver;
    private final SyncJobResourceCoordinator resourceCoordinator;
    private final Map<Long, JobRuntimeContext> runtimes = new ConcurrentHashMap<>();

    public SyncJobRuntimeManager(
            SyncJobRepository jobRepository,
            SyncJobLogRepository logRepository,
            SyncJobMapper mapper,
            List<FullLoadExporter> exporters,
            FullLoadImporter importer,
            List<ChangeEventSource> sources,
            ObjectProvider<ChangeEventSink> sinkProvider,
            ThreadPoolTaskExecutor jobExecutor,
            SystemSettingsService systemSettingsService,
            SourceTableMappingResolver tableMappingResolver,
            SyncJobResourceCoordinator resourceCoordinator
    ) {
        this.jobRepository = jobRepository;
        this.logRepository = logRepository;
        this.mapper = mapper;
        this.exporters = exporters;
        this.importer = importer;
        this.sources = sources;
        this.sinkProvider = sinkProvider;
        this.jobExecutor = jobExecutor;
        this.systemSettingsService = systemSettingsService;
        this.tableMappingResolver = tableMappingResolver;
        this.resourceCoordinator = resourceCoordinator;
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
                systemSettingsService.getDeploymentArchitecture(),
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
        log.info("Starting sync job {} with mode {} and source {}", jobId, definition.syncMode(), definition.source().databaseType());
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
        log.info("Stopped sync job {}", jobId);
    }

    private void executeJob(SyncJobEntity entity, SyncJobDefinition definition, ProgressReporter reporter, JobRuntimeContext context) {
        try {
            SyncJobDefinition resolvedDefinition = tableMappingResolver.resolve(definition, reporter);
            validate(resolvedDefinition, reporter);
            try (SyncJobResourceCoordinator.ResourceLease lease = resourceCoordinator.claim(entity.getId(), resolvedDefinition)) {
                reporter.log("INFO", "Claimed runtime resources: " + String.join(", ", lease.keys()));
                switch (resolvedDefinition.syncMode()) {
                    case FULL_ONLY -> runFullOnly(resolvedDefinition, reporter);
                    case INCREMENTAL_ONLY -> runIncrementalOnly(resolvedDefinition, reporter, context);
                    case FULL_AND_INCREMENTAL -> runFullAndIncremental(resolvedDefinition, reporter, context);
                }
            }
            if (!context.getStopRequested().get() && resolvedDefinition.syncMode() == SyncMode.FULL_ONLY) {
                markCompleted(entity.getId(), "Full load completed");
            }
        } catch (Exception ex) {
            log.error("Execution failed for sync job {}", entity.getId(), ex);
            markFailed(entity.getId(), ex);
        } finally {
            if (definition.syncMode() == SyncMode.FULL_ONLY || context.getStopRequested().get()) {
                runtimes.remove(entity.getId());
            }
        }
    }

    private void validate(SyncJobDefinition definition, ProgressReporter reporter) {
        reporter.updatePhase(JobPhase.VALIDATING, 5, "Validating job definition");
        if (definition.source().databaseType() == SourceDatabaseType.CSV) {
            if (definition.syncMode() != SyncMode.FULL_ONLY) {
                throw new IllegalArgumentException("CSV source only supports FULL_ONLY mode");
            }
            if (definition.fullLoad() == null
                    || definition.fullLoad().exportBaseDir() == null
                    || definition.fullLoad().exportBaseDir().isBlank()) {
                throw new IllegalArgumentException("CSV source requires a directory path");
            }
            reporter.updatePhase(JobPhase.VALIDATING, 100, "Validation finished");
            return;
        }
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
        ChangeEventSource source = selectSource(definition);
        if (source.writesDirectlyToTarget(definition)) {
            ChangeCaptureHandle handle = source.start(definition, NO_OP_SINK, reporter);
            context.setCaptureHandle(handle);
            handle.awaitStop();
            if (!context.getStopRequested().get()) {
                markCompleted(definition.jobId(), "Incremental sync stopped normally");
            }
            return;
        }

        ChangeEventSink sink = sinkProvider.getObject();
        sink.open(definition, reporter);
        try {
            ChangeCaptureHandle handle = source.start(definition, sink, reporter);
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
        SyncJobDefinition enrichedDefinition = captureFullLoadIncrementalStartPoint(definition, reporter);
        ChangeEventSource source = selectSource(definition);
        if (source.managesFullAndIncremental(enrichedDefinition)) {
            ChangeCaptureHandle handle = source.start(enrichedDefinition, NO_OP_SINK, reporter);
            context.setCaptureHandle(handle);
            handle.awaitStop();
            if (!context.getStopRequested().get()) {
                markCompleted(enrichedDefinition.jobId(), "Managed full + incremental sync stopped normally");
            }
            return;
        }

        FullLoadExporter exporter = selectExporter(enrichedDefinition);
        var exportResult = exporter.export(enrichedDefinition, reporter);
        importer.importCsv(enrichedDefinition, exportResult, reporter);
        reporter.updatePhase(JobPhase.STARTING_INCREMENTAL, 100, "Full load imported, starting incremental sync");

        if (source.writesDirectlyToTarget(enrichedDefinition)) {
            ChangeCaptureHandle handle = source.start(enrichedDefinition, NO_OP_SINK, reporter);
            context.setCaptureHandle(handle);
            handle.awaitStop();
            if (!context.getStopRequested().get()) {
                markCompleted(enrichedDefinition.jobId(), "Full load imported and incremental sync stopped normally");
            }
            return;
        }

        ChangeEventSink sink = sinkProvider.getObject();
        sink.open(enrichedDefinition, reporter);
        try {
            ChangeCaptureHandle handle = source.start(enrichedDefinition, sink, reporter);
            context.setCaptureHandle(handle);
            handle.awaitStop();
            if (!context.getStopRequested().get()) {
                markCompleted(enrichedDefinition.jobId(), "Full load imported and incremental sync stopped normally");
            }
        } finally {
            sink.close();
        }
    }

    private SyncJobDefinition captureFullLoadIncrementalStartPoint(SyncJobDefinition definition, ProgressReporter reporter) {
        if (definition.syncMode() != SyncMode.FULL_AND_INCREMENTAL) {
            return definition;
        }
        if (definition.source().databaseType() == SourceDatabaseType.MYSQL) {
            return captureMySqlBinlogPosition(definition, reporter);
        }
        if (definition.source().databaseType() == SourceDatabaseType.MARIADB) {
            return captureMariaDbBinlogPosition(definition, reporter);
        }
        if (definition.source().databaseType() == SourceDatabaseType.ORACLE) {
            return captureOracleScn(definition, reporter);
        }
        return definition;
    }

    private SyncJobDefinition captureMySqlBinlogPosition(SyncJobDefinition definition, ProgressReporter reporter) {
        try (Connection connection = DriverManager.getConnection(
                JdbcConnectionSupport.resolveSourceJdbcUrl(definition.source()),
                definition.source().username(),
                definition.source().password()
        );
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SHOW MASTER STATUS")) {
            if (!resultSet.next()) {
                throw new IllegalStateException("SHOW MASTER STATUS returned no result");
            }
            String file = resultSet.getString("File");
            String position = resultSet.getString("Position");
            String logPosition = file + ":" + position;
            reporter.updateLogPosition(logPosition, "Captured MySQL binlog position before full export");
            reporter.log("INFO", "Captured MySQL binlog position " + logPosition);
            return withIncrementalProperties(definition, Map.of(
                    "mysqlBinlogFilename", file,
                    "mysqlBinlogPosition", position
            ));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to capture MySQL binlog position before full export", ex);
        }
    }

    private SyncJobDefinition captureOracleScn(SyncJobDefinition definition, ProgressReporter reporter) {
        try (Connection connection = DriverManager.getConnection(
                JdbcConnectionSupport.resolveSourceJdbcUrl(definition.source()),
                definition.source().username(),
                definition.source().password()
        );
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT CURRENT_SCN FROM V$DATABASE")) {
            if (!resultSet.next()) {
                throw new IllegalStateException("SELECT CURRENT_SCN FROM V$DATABASE returned no result");
            }
            String scn = resultSet.getString(1);
            reporter.updateLogPosition("SCN=" + scn, "Captured Oracle SCN before full export");
            reporter.log("INFO", "Captured Oracle SCN " + scn);
            return withIncrementalProperties(definition, Map.of("oracleStartScn", scn));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to capture Oracle SCN before full export", ex);
        }
    }

    private SyncJobDefinition captureMariaDbBinlogPosition(SyncJobDefinition definition, ProgressReporter reporter) {
        try (Connection connection = DriverManager.getConnection(
                JdbcConnectionSupport.resolveSourceJdbcUrl(definition.source()),
                definition.source().username(),
                definition.source().password()
        );
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SHOW MASTER STATUS")) {
            if (!resultSet.next()) {
                throw new IllegalStateException("SHOW MASTER STATUS returned no result");
            }
            String file = resultSet.getString("File");
            String position = resultSet.getString("Position");
            String logPosition = file + ":" + position;
            reporter.updateLogPosition(logPosition, "Captured MariaDB binlog position before full export");
            reporter.log("INFO", "Captured MariaDB binlog position " + logPosition);
            return withIncrementalProperties(definition, Map.of(
                    "mysqlBinlogFilename", file,
                    "mysqlBinlogPosition", position
            ));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to capture MariaDB binlog position before full export", ex);
        }
    }

    private SyncJobDefinition withIncrementalProperties(SyncJobDefinition definition, Map<String, String> overrides) {
        Map<String, String> additionalProperties = new LinkedHashMap<>();
        if (definition.incremental().additionalProperties() != null) {
            additionalProperties.putAll(definition.incremental().additionalProperties());
        }
        additionalProperties.putAll(overrides);
        IncrementalConfig incrementalConfig = new IncrementalConfig(
                definition.incremental().serverName(),
                definition.incremental().slotName(),
                definition.incremental().publicationName(),
                definition.incremental().offsetStoragePath(),
                definition.incremental().pollingIntervalSeconds(),
                definition.incremental().batchSize(),
                additionalProperties
        );
        return new SyncJobDefinition(
                definition.jobId(),
                definition.jobName(),
                definition.syncMode(),
                definition.deploymentArchitecture(),
                definition.source(),
                definition.target(),
                definition.tableMappings(),
                definition.fullLoad(),
                incrementalConfig
        );
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
