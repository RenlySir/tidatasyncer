package com.example.sync.connectors.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.sync.core.config.FullLoadConfig;
import com.example.sync.core.config.IncrementalConfig;
import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TargetConnectionProperties;
import com.example.sync.core.model.DeploymentArchitecture;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.model.SyncMode;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SourceTableMappingResolverTest {

    private final SourceTableMappingResolver resolver = new SourceTableMappingResolver();

    @Test
    void shouldDiscoverAllTablesForDatabaseWideMySqlSync() throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet tables = mock(ResultSet.class);
        ResultSet primaryKeys = mock(ResultSet.class);
        when(metaData.getTables("source_db", "source_db", "%", new String[]{"TABLE"})).thenReturn(tables);
        when(tables.next()).thenReturn(true, true, false);
        when(tables.getString("TABLE_CAT")).thenReturn("source_db", "source_db");
        when(tables.getString("TABLE_SCHEM")).thenReturn("source_db", "source_db");
        when(tables.getString("TABLE_NAME")).thenReturn("orders", "customers");
        when(metaData.getPrimaryKeys("source_db", "source_db", "orders")).thenReturn(primaryKeys);
        when(metaData.getPrimaryKeys("source_db", "source_db", "customers")).thenReturn(primaryKeys);
        when(primaryKeys.next()).thenReturn(true, false, true, false);
        when(primaryKeys.getShort("KEY_SEQ")).thenReturn((short) 1, (short) 1);
        when(primaryKeys.getString("COLUMN_NAME")).thenReturn("id", "customer_id");

        List<com.example.sync.core.config.TableMapping> mappings = resolver.discoverJdbcMappings(jobDefinition(), metaData);

        assertThat(mappings).hasSize(2);
        assertThat(mappings).extracting(com.example.sync.core.config.TableMapping::sourceTable)
                .containsExactly("customers", "orders");
        assertThat(mappings).extracting(com.example.sync.core.config.TableMapping::targetDatabase)
                .containsOnly("target_db");
    }

    private SyncJobDefinition jobDefinition() {
        return new SyncJobDefinition(
                1L,
                "mysql-to-tidb",
                SyncMode.FULL_AND_INCREMENTAL,
                DeploymentArchitecture.AMD64,
                new SourceConnectionProperties(
                        SourceDatabaseType.MYSQL,
                        "127.0.0.1",
                        3306,
                        "source_db",
                        "source_db",
                        "root",
                        "root",
                        "jdbc:mysql://127.0.0.1:3306/source_db",
                        "",
                        ""
                ),
                new TargetConnectionProperties(
                        "127.0.0.1",
                        4000,
                        "target_db",
                        "root",
                        "root",
                        "jdbc:mysql://127.0.0.1:4000/target_db",
                        "",
                        "tidb-lightning"
                ),
                List.of(),
                new FullLoadConfig("dumpling", "./work/export", 1000, 1, Map.of()),
                new IncrementalConfig("sync_server", "sync_slot", "sync_pub", "./work/offsets/offset.dat", 5, 500, Map.of())
        );
    }
}
