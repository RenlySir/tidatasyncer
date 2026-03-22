package com.example.sync.connectors.cdc;

import com.example.sync.connectors.util.MongoConnectionSupport;
import com.example.sync.connectors.util.MongoDocumentMapper;
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
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class MongoDbChangeStreamSource implements ChangeEventSource {

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.MONGODB;
    }

    @Override
    public ChangeCaptureHandle start(SyncJobDefinition definition, ChangeEventSink sink, ProgressReporter reporter) throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch stopLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "mongodb-change-stream-" + definition.jobId()));

        var future = executorService.submit(() -> {
            reporter.updatePhase(JobPhase.RUNNING_INCREMENTAL, 100, "MongoDB change stream started");
            String connectionString = MongoConnectionSupport.resolveConnectionString(
                    definition.source(),
                    definition.incremental().additionalProperties()
            );
            try (MongoClient client = MongoClients.create(connectionString)) {
                MongoDatabase database = client.getDatabase(definition.source().databaseName());
                List<String> collections = definition.tableMappings().stream()
                        .map(TableMapping::sourceTable)
                        .filter(Objects::nonNull)
                        .toList();

                ChangeStreamIterable<Document> changeStream = database.watch(List.of(
                                Aggregates.match(Filters.in("operationType", List.of("insert", "update", "replace", "delete"))),
                                Aggregates.match(Filters.in("ns.coll", collections))
                        ))
                        .fullDocument(FullDocument.UPDATE_LOOKUP);

                try (MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = changeStream.cursor()) {
                    while (running.get() && !reporter.isStopRequested()) {
                        ChangeStreamDocument<Document> change = cursor.tryNext();
                        if (change == null) {
                            Thread.sleep(500L);
                            continue;
                        }
                        StandardChangeEvent event = mapEvent(definition, change);
                        reporter.updateLag(calculateLag(change.getClusterTime()), "MongoDB CDC lag updated");
                        if (change.getResumeToken() != null) {
                            reporter.updateLogPosition(change.getResumeToken().toBsonDocument().toJson(), "MongoDB resume token updated");
                        }
                        reporter.updateLatestEvent(event);
                        sink.accept(event);
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                reporter.log("ERROR", "MongoDB change stream failed: " + ex.getMessage());
            } finally {
                stopLatch.countDown();
            }
        });

        return new SimpleChangeCaptureHandle(executorService, future, () -> running.set(false), stopLatch);
    }

    private StandardChangeEvent mapEvent(SyncJobDefinition definition, ChangeStreamDocument<Document> change) {
        TableMapping mapping = resolveMapping(definition, change);
        Document fullDocument = change.getFullDocument();
        Document keyDocument = change.getDocumentKey() == null ? null : Document.parse(change.getDocumentKey().toJson());
        Map<String, Object> keys = MongoDocumentMapper.toFlatMap(keyDocument, mapping == null ? List.of() : mapping.primaryKeys());
        Map<String, Object> after = switch (change.getOperationType()) {
            case INSERT, UPDATE, REPLACE -> MongoDocumentMapper.toFlatMap(
                    fullDocument,
                    mapping == null ? List.of() : mapping.includedColumns()
            );
            default -> Map.of();
        };

        Instant eventTime = clusterTimeToInstant(change.getClusterTime());
        String database = change.getNamespace() == null ? definition.source().databaseName() : change.getNamespace().getDatabaseName();
        String collection = change.getNamespace() == null ? null : change.getNamespace().getCollectionName();
        return new StandardChangeEvent(
                database,
                database,
                collection,
                keys,
                Map.of(),
                after,
                mapOperation(change),
                eventTime,
                Instant.now()
        );
    }

    private TableMapping resolveMapping(SyncJobDefinition definition, ChangeStreamDocument<Document> change) {
        String database = change.getNamespace() == null ? definition.source().databaseName() : change.getNamespace().getDatabaseName();
        String collection = change.getNamespace() == null ? null : change.getNamespace().getCollectionName();
        return definition.tableMappings().stream()
                .filter(mapping -> Objects.equals(mapping.sourceTable(), collection))
                .filter(mapping -> mapping.sourceSchema() == null || mapping.sourceSchema().isBlank() || Objects.equals(mapping.sourceSchema(), database))
                .findFirst()
                .orElse(null);
    }

    private ChangeOperation mapOperation(ChangeStreamDocument<Document> change) {
        return switch (change.getOperationType()) {
            case INSERT -> ChangeOperation.INSERT;
            case UPDATE, REPLACE -> ChangeOperation.UPDATE;
            case DELETE -> ChangeOperation.DELETE;
            default -> ChangeOperation.SNAPSHOT;
        };
    }

    private long calculateLag(BsonTimestamp clusterTime) {
        return Math.max(0L, Instant.now().toEpochMilli() - clusterTimeToInstant(clusterTime).toEpochMilli());
    }

    private Instant clusterTimeToInstant(BsonTimestamp clusterTime) {
        if (clusterTime == null) {
            return Instant.now();
        }
        return Instant.ofEpochSecond(clusterTime.getTime());
    }
}
