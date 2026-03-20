package com.example.sync.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.sync.admin.domain.SyncJobEntity;
import com.example.sync.admin.domain.SyncJobStatus;
import com.example.sync.admin.repository.SyncJobLogRepository;
import com.example.sync.admin.repository.SyncJobRepository;
import com.example.sync.admin.support.SyncJobFixtures;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.JobPhase;
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

    private SyncJobRuntimeManager runtimeManager;
    private SyncJobEntity entity;
    private SyncJobDefinition definition;

    @BeforeEach
    void setUp() {
        runtimeManager = new SyncJobRuntimeManager(
                jobRepository,
                logRepository,
                mapper,
                List.of(exporter),
                importer,
                List.of(changeEventSource),
                sinkProvider,
                jobExecutor
        );
        definition = SyncJobFixtures.jobDefinition();
        entity = SyncJobFixtures.persistedJob(mapper.toJson(definition));
        when(jobRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(jobRepository.save(any(SyncJobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exporter.supports(any())).thenReturn(true);
        when(changeEventSource.supports(any())).thenReturn(true);
        when(sinkProvider.getObject()).thenReturn(changeEventSink);
    }

    @Test
    void startShouldRunFullOnlyJobAndMarkCompleted() throws Exception {
        SyncJobDefinition fullOnlyDefinition = new SyncJobDefinition(
                definition.jobId(),
                definition.jobName(),
                SyncMode.FULL_ONLY,
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
}
