package com.example.sync.connectors.cdc;

import com.example.sync.connectors.util.SimpleChangeCaptureHandle;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.model.ChangeOperation;
import com.example.sync.core.model.JobPhase;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.runtime.StandardChangeEvent;
import com.example.sync.core.spi.ChangeCaptureHandle;
import com.example.sync.core.spi.ChangeEventSink;
import com.example.sync.core.spi.ChangeEventSource;
import com.example.sync.core.spi.ProgressReporter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class HanaPollingChangeEventSource implements ChangeEventSource {

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.HANA;
    }

    @Override
    public ChangeCaptureHandle start(SyncJobDefinition definition, ChangeEventSink sink, ProgressReporter reporter) throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch stopLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "hana-polling-job-" + definition.jobId()));

        var future = executorService.submit(() -> {
            reporter.updatePhase(JobPhase.RUNNING_INCREMENTAL, 100, "HANA incremental polling started");
            Map<String, Object> offsets = new HashMap<>();

            try (Connection connection = DriverManager.getConnection(
                    definition.source().jdbcUrl(),
                    definition.source().username(),
                    definition.source().password()
            )) {
                while (running.get() && !reporter.isStopRequested()) {
                    for (TableMapping mapping : definition.tableMappings()) {
                        pollTable(connection, definition, mapping, sink, reporter, offsets);
                    }
                    Thread.sleep((definition.incremental().pollingIntervalSeconds() == null ? 5 : definition.incremental().pollingIntervalSeconds()) * 1000L);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                reporter.log("ERROR", "HANA polling CDC failed: " + ex.getMessage());
            } finally {
                stopLatch.countDown();
            }
        });

        return new SimpleChangeCaptureHandle(executorService, future, () -> running.set(false), stopLatch);
    }

    private void pollTable(
            Connection connection,
            SyncJobDefinition definition,
            TableMapping mapping,
            ChangeEventSink sink,
            ProgressReporter reporter,
            Map<String, Object> offsets
    ) throws Exception {
        String watermarkColumn = mapping.incrementalColumn();
        if (watermarkColumn == null || watermarkColumn.isBlank()) {
            reporter.log("WARN", "Skipping HANA table without incrementalColumn: " + mapping.sourceTable());
            return;
        }
        String offsetKey = mapping.sourceSchema() + "." + mapping.sourceTable();
        Object lastOffset = offsets.get(offsetKey);
        String whereClause = lastOffset == null ? "" : " where " + watermarkColumn + " > '" + lastOffset + "'";
        String sql = "select * from \"" + mapping.sourceSchema() + "\".\"" + mapping.sourceTable() + "\"" + whereClause
                + " order by " + watermarkColumn + " limit " + (definition.incremental().batchSize() == null ? 500 : definition.incremental().batchSize());
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            ResultSetMetaData metaData = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> payload = new HashMap<>();
                Map<String, Object> keys = new HashMap<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    String column = metaData.getColumnLabel(i);
                    Object value = rs.getObject(i);
                    payload.put(column, value);
                    if (mapping.primaryKeys() != null && mapping.primaryKeys().contains(column)) {
                        keys.put(column, value);
                    }
                    if (column.equalsIgnoreCase(watermarkColumn)) {
                        offsets.put(offsetKey, value);
                    }
                }
                StandardChangeEvent event = new StandardChangeEvent(
                        definition.source().databaseName(),
                        mapping.sourceSchema(),
                        mapping.sourceTable(),
                        keys,
                        Map.of(),
                        payload,
                        ChangeOperation.UPDATE,
                        Instant.now(),
                        Instant.now()
                );
                reporter.updateLatestEvent(event);
                sink.accept(event);
            }
        }
    }
}
