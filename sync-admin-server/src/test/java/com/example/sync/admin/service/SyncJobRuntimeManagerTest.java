package com.example.sync.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sync.admin.domain.SyncJobEntity;
import com.example.sync.admin.domain.SyncJobStatus;
import com.example.sync.admin.repository.SyncJobLogRepository;
import com.example.sync.admin.repository.SyncJobRepository;
import com.example.sync.admin.support.SyncJobFixtures;
import com.example.sync.connectors.util.SourceTableMappingResolver;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.DeploymentArchitecture;
import com.example.sync.core.model.JobPhase;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.model.SyncMode;
import com.example.sync.core.spi.ChangeCaptureHandle;
import com.example.sync.core.spi.ChangeEventSink;
import com.example.sync.core.spi.ChangeEventSource;
import com.example.sync.core.spi.CsvExportResult;
import com.example.sync.core.spi.FullLoadExporter;
import com.example.sync.core.spi.FullLoadImporter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class SyncJobRuntimeManagerTest {

    private final SyncJobRepository jobRepository = mock(SyncJobRepository.class);
    private final SyncJobLogRepository logRepository = mock(SyncJobLogRepository.class);
    private final SyncJobMapper mapper = new SyncJobMapper(new com.fasterxml.jackson.databind.ObjectMapper());
    private final FullLoadExporter exporter = mock(FullLoadExporter.class);
    private final FullLoadImporter importer = mock(FullLoadImporter.class);
    private final ChangeEventSource changeEventSource = mock(ChangeEventSource.class);
    private final ChangeEventSink changeEventSink = mock(ChangeEventSink.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ChangeEventSink> sinkProvider = mock(ObjectProvider.class);
    private final ThreadPoolTaskExecutor jobExecutor = mock(ThreadPoolTaskExecutor.class);
    private final SystemSettingsService systemSettingsService = mock(SystemSettingsService.class);
    private final SourceTableMappingResolver tableMappingResolver = mock(SourceTableMappingResolver.class);
    private final SyncJobResourceCoordinator resourceCoordinator = new SyncJobResourceCoordinator();

    private SyncJobRuntimeManager runtimeManager;
    private SyncJobEntity entity;
    private SyncJobDefinition definition;

    @BeforeEach
    void setUp() throws Exception {
        runtimeManager = new SyncJobRuntimeManager(
                jobRepository,
                logRepository,
                mapper,
                List.of(exporter),
                importer,
                List.of(changeEventSource),
                sinkProvider,
                jobExecutor,
                systemSettingsService,
                tableMappingResolver,
                resourceCoordinator
        );
        definition = SyncJobFixtures.jobDefinition();
        entity = SyncJobFixtures.persistedJob(mapper.toJson(definition));
        when(jobRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(jobRepository.save(any(SyncJobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exporter.supports(any())).thenReturn(true);
        when(changeEventSource.supports(any())).thenReturn(true);
        when(sinkProvider.getObject()).thenReturn(changeEventSink);
        when(systemSettingsService.getDeploymentArchitecture()).thenReturn(DeploymentArchitecture.AMD64);
        when(tableMappingResolver.resolve(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void startShouldRunFullOnlyJobAndMarkCompleted() throws Exception {
        SyncJobDefinition fullOnlyDefinition = new SyncJobDefinition(
                definition.jobId(),
                definition.jobName(),
                SyncMode.FULL_ONLY,
                definition.deploymentArchitecture(),
                definition.source(),
                definition.target(),
                definition.tableMappings(),
                definition.fullLoad(),
                definition.incremental()
        );
        entity.setSyncMode(SyncMode.FULL_ONLY);
        entity.setDefinitionJson(mapper.toJson(fullOnlyDefinition));

        when(exporter.export(any(), any())).thenReturn(new CsvExportResult(Path.of("work/export"), List.of(Path.of("orders.csv")), 10));
        when(jobExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });

        runtimeManager.start(1L);

        assertThat(entity.getStatus()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(entity.getPhase()).isEqualTo(JobPhase.COMPLETED);
        verify(importer).importCsv(any(), any(), any());
    }

    @Test
    void startShouldFailWhenNoTableMappingsPresent() throws Exception {
        SyncJobDefinition invalidDefinition = new SyncJobDefinition(
                definition.jobId(),
                definition.jobName(),
                SyncMode.FULL_ONLY,
                definition.deploymentArchitecture(),
                definition.source(),
                definition.target(),
                List.of(),
                definition.fullLoad(),
                definition.incremental()
        );
        entity.setDefinitionJson(mapper.toJson(invalidDefinition));
        entity.setSyncMode(SyncMode.FULL_ONLY);
        when(jobExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });

        runtimeManager.start(1L);

        assertThat(entity.getStatus()).isEqualTo(SyncJobStatus.FAILED);
        assertThat(entity.getPhase()).isEqualTo(JobPhase.FAILED);
        assertThat(entity.getLastError()).contains("At least one table mapping is required");
        verify(importer, never()).importCsv(any(), any(), any());
    }

    @Test
    void stopShouldCancelFutureAndMarkStopped() {
        Future<?> future = mock(Future.class);
        when(jobExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> future);

        runtimeManager.start(1L);
        runtimeManager.stop(1L);

        assertThat(entity.getStatus()).isEqualTo(SyncJobStatus.STOPPED);
        assertThat(entity.getPhase()).isEqualTo(JobPhase.STOPPED);
        verify(future).cancel(true);
    }

    @Test
    void startShouldRejectSecondLaunchWhileJobIsTracked() {
        Future<?> future = mock(Future.class);
        when(jobExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> future);

        runtimeManager.start(1L);

        assertThatThrownBy(() -> runtimeManager.start(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");
    }

    @Test
    void startShouldRunIncrementalOnlyJobAndCloseSink() throws Exception {
        ChangeCaptureHandle handle = mock(ChangeCaptureHandle.class);
        doNothing().when(handle).awaitStop();

        SyncJobDefinition incrementalDefinition = new SyncJobDefinition(
                definition.jobId(),
                definition.jobName(),
                SyncMode.INCREMENTAL_ONLY,
                definition.deploymentArchitecture(),
                definition.source(),
                definition.target(),
                definition.tableMappings(),
                definition.fullLoad(),
                definition.incremental()
        );
        entity.setDefinitionJson(mapper.toJson(incrementalDefinition));
        entity.setSyncMode(SyncMode.INCREMENTAL_ONLY);
        when(changeEventSource.start(any(), any(), any())).thenReturn(handle);
        when(jobExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });

        runtimeManager.start(1L);

        verify(changeEventSource).start(any(), any(), any());
        verify(handle).awaitStop();
        verify(changeEventSink).open(any(), any());
        verify(changeEventSink).close();
        assertThat(entity.getStatus()).isEqualTo(SyncJobStatus.COMPLETED);
    }

    @Test
    void startShouldRunManagedFullAndIncrementalWithoutOpeningSink() throws Exception {
        ChangeCaptureHandle handle = mock(ChangeCaptureHandle.class);
        doNothing().when(handle).awaitStop();
        SyncJobDefinition postgresDefinition = new SyncJobDefinition(
                definition.jobId(),
                definition.jobName(),
                definition.syncMode(),
                definition.deploymentArchitecture(),
                new com.example.sync.core.config.SourceConnectionProperties(
                        SourceDatabaseType.POSTGRESQL,
                        definition.source().host(),
                        5432,
                        definition.source().databaseName(),
                        "public",
                        definition.source().username(),
                        definition.source().password(),
                        "jdbc:postgresql://127.0.0.1:5432/source_db",
                        "",
                        definition.source().commandTemplate()
                ),
                definition.target(),
                definition.tableMappings(),
                definition.fullLoad(),
                definition.incremental()
        );
        entity.setDefinitionJson(mapper.toJson(postgresDefinition));

        when(changeEventSource.managesFullAndIncremental(any())).thenReturn(true);
        when(changeEventSource.writesDirectlyToTarget(any())).thenReturn(true);
        when(changeEventSource.start(any(), any(), any())).thenReturn(handle);
        when(jobExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });

        runtimeManager.start(1L);

        verify(changeEventSource).start(any(), any(), any());
        verify(changeEventSink, never()).open(any(), any());
        verify(exporter, never()).export(any(), any());
        verify(importer, never()).importCsv(any(), any(), any());
        assertThat(entity.getStatus()).isEqualTo(SyncJobStatus.COMPLETED);
    }

    @Test
    void startShouldImportFullLoadBeforeStartingIncrementalSync() throws Exception {
        ChangeCaptureHandle handle = mock(ChangeCaptureHandle.class);
        doNothing().when(handle).awaitStop();
        SyncJobDefinition postgresDefinition = new SyncJobDefinition(
                definition.jobId(),
                definition.jobName(),
                definition.syncMode(),
                definition.deploymentArchitecture(),
                new com.example.sync.core.config.SourceConnectionProperties(
                        SourceDatabaseType.POSTGRESQL,
                        definition.source().host(),
                        5432,
                        definition.source().databaseName(),
                        "public",
                        definition.source().username(),
                        definition.source().password(),
                        "jdbc:postgresql://127.0.0.1:5432/source_db",
                        "",
                        definition.source().commandTemplate()
                ),
                definition.target(),
                definition.tableMappings(),
                definition.fullLoad(),
                definition.incremental()
        );
        entity.setDefinitionJson(mapper.toJson(postgresDefinition));
        when(exporter.export(any(), any())).thenReturn(new CsvExportResult(Path.of("work/export"), List.of(Path.of("orders.csv")), 10));
        when(changeEventSource.start(any(), any(), any())).thenReturn(handle);
        when(jobExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });

        runtimeManager.start(1L);

        InOrder inOrder = inOrder(exporter, importer, changeEventSink, changeEventSource);
        inOrder.verify(exporter).export(any(), any());
        inOrder.verify(importer).importCsv(any(), any(), any());
        inOrder.verify(changeEventSink).open(any(), any());
        inOrder.verify(changeEventSource).start(any(), any(), any());
        verify(changeEventSink).close();
        assertThat(entity.getStatus()).isEqualTo(SyncJobStatus.COMPLETED);
    }

    @Test
    void startShouldUseSavedSystemDeploymentArchitecture() throws Exception {
        SyncJobDefinition armDefinition = new SyncJobDefinition(
                definition.jobId(),
                definition.jobName(),
                SyncMode.FULL_ONLY,
                DeploymentArchitecture.ARM64,
                definition.source(),
                definition.target(),
                definition.tableMappings(),
                definition.fullLoad(),
                definition.incremental()
        );
        entity.setSyncMode(SyncMode.FULL_ONLY);
        entity.setDefinitionJson(mapper.toJson(armDefinition));
        when(systemSettingsService.getDeploymentArchitecture()).thenReturn(DeploymentArchitecture.AMD64);

        when(exporter.export(any(), any())).thenAnswer(invocation -> {
            SyncJobDefinition runtimeDefinition = invocation.getArgument(0);
            assertThat(runtimeDefinition.deploymentArchitecture()).isEqualTo(DeploymentArchitecture.AMD64);
            return new CsvExportResult(Path.of("work/export"), List.of(Path.of("orders.csv")), 10);
        });
        when(jobExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });

        runtimeManager.start(1L);

        verify(importer).importCsv(any(), any(), any());
    }

    @Test
    void startShouldResolveDatabaseWideMappingsWhenNoExplicitTablesProvided() throws Exception {
        SyncJobDefinition databaseWideDefinition = new SyncJobDefinition(
                definition.jobId(),
                definition.jobName(),
                SyncMode.FULL_ONLY,
                definition.deploymentArchitecture(),
                definition.source(),
                definition.target(),
                List.of(),
                definition.fullLoad(),
                definition.incremental()
        );
        SyncJobDefinition resolvedDefinition = new SyncJobDefinition(
                databaseWideDefinition.jobId(),
                databaseWideDefinition.jobName(),
                databaseWideDefinition.syncMode(),
                databaseWideDefinition.deploymentArchitecture(),
                databaseWideDefinition.source(),
                databaseWideDefinition.target(),
                definition.tableMappings(),
                databaseWideDefinition.fullLoad(),
                databaseWideDefinition.incremental()
        );
        entity.setSyncMode(SyncMode.FULL_ONLY);
        entity.setDefinitionJson(mapper.toJson(databaseWideDefinition));
        when(tableMappingResolver.resolve(any(), any())).thenReturn(resolvedDefinition);
        when(exporter.export(any(), any())).thenReturn(new CsvExportResult(Path.of("work/export"), List.of(Path.of("orders.csv")), 10));
        when(jobExecutor.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });

        runtimeManager.start(1L);

        verify(tableMappingResolver).resolve(any(), any());
        verify(exporter).export(any(), any());
        assertThat(entity.getStatus()).isEqualTo(SyncJobStatus.COMPLETED);
    }
}
