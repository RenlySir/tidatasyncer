package com.example.sync.admin.support;

import com.example.sync.admin.domain.SyncJobEntity;
import com.example.sync.admin.domain.SyncJobStatus;
import com.example.sync.admin.dto.SyncJobUpsertRequest;
import com.example.sync.core.config.FullLoadConfig;
import com.example.sync.core.config.IncrementalConfig;
import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.config.TargetConnectionProperties;
import com.example.sync.core.model.JobPhase;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.model.SyncMode;
import java.util.List;
import java.util.Map;

public final class SyncJobFixtures {

    private SyncJobFixtures() {
    }

    public static SyncJobDefinition jobDefinition() {
        return new SyncJobDefinition(
                1L,
                "mysql-to-tidb",
                SyncMode.FULL_AND_INCREMENTAL,
                new SourceConnectionProperties(
                        SourceDatabaseType.MYSQL,
                        "127.0.0.1",
                        3306,
                        "source_db",
                        "public",
                        "root",
                        "root",
                        "jdbc:mysql://127.0.0.1:3306/source_db",
                        "echo export > ${file}"
                ),
                new TargetConnectionProperties(
                        "127.0.0.1",
                        4000,
                        "target_db",
                        "root",
                        "root",
                        "jdbc:mysql://127.0.0.1:4000/target_db",
                        "tidb-lightning"
                ),
                List.of(new TableMapping(
                        "source_db",
                        "public",
                        "orders",
                        "target_db",
                        "orders",
                        List.of("id"),
                        "updated_at",
                        List.of("id", "status", "updated_at"),
                        Map.of("status", "order_status")
                )),
                new FullLoadConfig(
                        "mysqldump",
                        "./work/export",
                        1000,
                        1,
                        Map.of()
                ),
                new IncrementalConfig(
                        "sync_server",
                        "sync_slot",
                        "sync_pub",
                        "./work/offsets/offset.dat",
                        5,
                        500,
                        Map.of("snapshot.mode", "initial")
                )
        );
    }

    public static SyncJobUpsertRequest upsertRequest() {
        return new SyncJobUpsertRequest("mysql-to-tidb", jobDefinition());
    }

    public static SyncJobEntity persistedJob(String definitionJson) {
        SyncJobEntity entity = new SyncJobEntity();
        entity.setId(1L);
        entity.setName("mysql-to-tidb");
        entity.setSyncMode(SyncMode.FULL_AND_INCREMENTAL);
        entity.setStatus(SyncJobStatus.DRAFT);
        entity.setPhase(JobPhase.CREATED);
        entity.setDefinitionJson(definitionJson);
        entity.setProgressPercent(0);
        return entity;
    }
}
