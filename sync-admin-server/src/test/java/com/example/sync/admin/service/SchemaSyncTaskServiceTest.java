package com.example.sync.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sync.admin.domain.ConnectionProfileEntity;
import com.example.sync.admin.domain.DatabaseEndpointType;
import com.example.sync.admin.dto.UnsupportedTypeItemResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaSyncTaskServiceTest {

    private final SchemaSyncTaskService service = new SchemaSyncTaskService(
            null,
            null,
            null,
            new SchemaTypeMappingService(),
            new ObjectMapper()
    );

    @Test
    void shouldAppendShardRowIdBitsWhenTableHasNoPrimaryKey() {
        String ddl = service.buildCreateTableStatement(
                sourceProfile(DatabaseEndpointType.ORACLE),
                "target_db",
                tableSchemaModel("orders", List.of(), false),
                Map.of(),
                new ArrayList<>()
        );

        assertThat(ddl).contains("SHARD_ROW_ID_BITS=4");
        assertThat(ddl).doesNotContain("PRIMARY KEY");
    }

    @Test
    void shouldNotAppendShardRowIdBitsWhenPrimaryKeyExists() {
        String ddl = service.buildCreateTableStatement(
                sourceProfile(DatabaseEndpointType.ORACLE),
                "target_db",
                tableSchemaModel("orders", List.of("ID"), false),
                Map.of(),
                new ArrayList<>()
        );

        assertThat(ddl).contains("PRIMARY KEY (`ID`)");
        assertThat(ddl).doesNotContain("SHARD_ROW_ID_BITS=4");
    }

    @Test
    void shouldStillCollectUnsupportedItemsWhileApplyingShardRule() {
        List<UnsupportedTypeItemResponse> unsupportedItems = new ArrayList<>();

        String ddl = service.buildCreateTableStatement(
                sourceProfile(DatabaseEndpointType.ORACLE),
                "target_db",
                tableSchemaModel("orders", List.of(), true),
                Map.of(),
                unsupportedItems
        );

        assertThat(ddl).contains("SHARD_ROW_ID_BITS=4");
        assertThat(unsupportedItems).hasSize(1);
        assertThat(unsupportedItems.get(0).sourceType()).isEqualTo("XMLTYPE");
    }

    private ConnectionProfileEntity sourceProfile(DatabaseEndpointType type) {
        ConnectionProfileEntity entity = new ConnectionProfileEntity();
        entity.setDatabaseType(type);
        entity.setSchemaName("APP");
        entity.setDatabaseName("source_db");
        entity.setUsername("app");
        return entity;
    }

    private SchemaSyncTaskService.TableSchemaModel tableSchemaModel(String tableName, List<String> primaryKeys, boolean unsupportedColumn) {
        List<SchemaSyncTaskService.ColumnSchemaModel> columns = new ArrayList<>();
        if (unsupportedColumn) {
            columns.add(new SchemaSyncTaskService.ColumnSchemaModel("PAYLOAD", "XMLTYPE", Types.SQLXML, 0, 0, true));
        } else {
            columns.add(new SchemaSyncTaskService.ColumnSchemaModel("ID", "NUMBER", Types.NUMERIC, 18, 0, false));
            columns.add(new SchemaSyncTaskService.ColumnSchemaModel("NAME", "VARCHAR2", Types.VARCHAR, 128, 0, true));
        }
        return new SchemaSyncTaskService.TableSchemaModel("source_db", "APP", tableName, columns, primaryKeys);
    }
}
