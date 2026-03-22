package com.example.sync.connectors.cdc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sync.core.config.FullLoadConfig;
import com.example.sync.core.config.IncrementalConfig;
import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.config.TableMapping;
import com.example.sync.core.config.TargetConnectionProperties;
import com.example.sync.core.model.DeploymentArchitecture;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.model.SyncMode;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class DebeziumChangeEventSourceTest {

    private final DebeziumChangeEventSource source = new DebeziumChangeEventSource();

    @Test
    void shouldSupportMySqlSource() {
        assertTrue(source.supports(jobDefinition()));
    }

    @Test
    void shouldBuildMySqlPropertiesForDebeziumConnector() {
        Properties properties = source.buildProperties(jobDefinition());

        assertEquals("io.debezium.connector.mysql.MySqlConnector", properties.getProperty("connector.class"));
        assertEquals("source_db", properties.getProperty("database.include.list"));
        assertEquals("source_db.orders,source_db.order_items", properties.getProperty("table.include.list"));
        assertEquals("6601", properties.getProperty("database.server.id"));
        assertEquals("no_data", properties.getProperty("snapshot.mode"));
        assertEquals("false", properties.getProperty("include.schema.changes"));
    }

    @Test
    void shouldIgnoreLegacyDmPropertiesWhenBuildingDebeziumConfig() {
        Properties properties = source.buildProperties(jobDefinition());

        assertNull(properties.getProperty("dmMasterAddr"));
        assertNull(properties.getProperty("dmTaskName"));
        assertEquals("8192", properties.getProperty("max.batch.size"));
    }

    @Test
    void shouldBuildOraclePropertiesForLogMinerAndXStreamModes() {
        Properties logMinerProperties = source.buildProperties(oracleDefinition(Map.of(
                DebeziumChangeEventSource.ORACLE_ADAPTER_ALIAS, "logminer",
                DebeziumChangeEventSource.ORACLE_PDB_NAME_ALIAS, "ORCLPDB1"
        )));
        assertEquals("io.debezium.connector.oracle.OracleConnector", logMinerProperties.getProperty("connector.class"));
        assertEquals("logminer", logMinerProperties.getProperty("database.connection.adapter"));
        assertEquals("ORCLPDB1", logMinerProperties.getProperty("database.pdb.name"));
        assertEquals("APP.ORDERS", logMinerProperties.getProperty("table.include.list"));

        Properties xStreamProperties = source.buildProperties(oracleDefinition(Map.of(
                DebeziumChangeEventSource.ORACLE_ADAPTER_ALIAS, "xstream",
                DebeziumChangeEventSource.ORACLE_OUT_SERVER_NAME_ALIAS, "dbzxout"
        )));
        assertEquals("xstream", xStreamProperties.getProperty("database.connection.adapter"));
        assertEquals("dbzxout", xStreamProperties.getProperty("database.out.server.name"));
    }

    @Test
    void shouldBuildSqlServerAndPostgreSqlPropertiesFollowingDebeziumConnectors() {
        Properties sqlServerProperties = source.buildProperties(sqlServerDefinition());
        assertEquals("io.debezium.connector.sqlserver.SqlServerConnector", sqlServerProperties.getProperty("connector.class"));
        assertEquals("erp", sqlServerProperties.getProperty("database.names"));
        assertEquals("erp.dbo.orders", sqlServerProperties.getProperty("table.include.list"));

        Properties postgresProperties = source.buildProperties(postgresDefinition());
        assertEquals("io.debezium.connector.postgresql.PostgresConnector", postgresProperties.getProperty("connector.class"));
        assertEquals("inventory", postgresProperties.getProperty("database.dbname"));
        assertEquals("pgoutput", postgresProperties.getProperty("plugin.name"));
        assertEquals("filtered", postgresProperties.getProperty("publication.autocreate.mode"));
        assertEquals("public.orders", postgresProperties.getProperty("table.include.list"));
    }

    @Test
    void shouldBuildMariaDbAndDb2PropertiesFollowingDebeziumConnectors() {
        Properties mariaDbProperties = source.buildProperties(mariaDbDefinition());
        assertEquals("io.debezium.connector.mariadb.MariaDbConnector", mariaDbProperties.getProperty("connector.class"));
        assertEquals("sales", mariaDbProperties.getProperty("database.include.list"));
        assertEquals("8601", mariaDbProperties.getProperty("database.server.id"));
        assertEquals("sales.orders", mariaDbProperties.getProperty("table.include.list"));

        Properties db2Properties = source.buildProperties(db2Definition());
        assertEquals("io.debezium.connector.db2.Db2Connector", db2Properties.getProperty("connector.class"));
        assertEquals("SAMPLE", db2Properties.getProperty("database.dbname"));
        assertEquals("APP.ORDERS", db2Properties.getProperty("table.include.list"));
    }

    private SyncJobDefinition jobDefinition() {
        return new SyncJobDefinition(
                1201L,
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
                        null
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
                List.of(
                        new TableMapping(
                                "source_db",
                                "source_db",
                                "orders",
                                "target_db",
                                "orders",
                                List.of("id"),
                                "updated_at",
                                List.of("id", "updated_at"),
                                Map.of()
                        ),
                        new TableMapping(
                                "source_db",
                                "source_db",
                                "order_items",
                                "target_db",
                                "order_items",
                                List.of("id"),
                                "updated_at",
                                List.of("id", "updated_at"),
                                Map.of()
                        )
                ),
                new FullLoadConfig(
                        "dumpling",
                        "./work/export",
                        1000,
                        1,
                        Map.of()
                ),
                new IncrementalConfig(
                        "sync_server",
                        "",
                        "",
                        "./work/offsets/offset.dat",
                        5,
                        500,
                        Map.of(
                                DebeziumChangeEventSource.MYSQL_SERVER_ID_ALIAS, "6601",
                                DebeziumChangeEventSource.MYSQL_SNAPSHOT_MODE_ALIAS, "no_data",
                                "dmMasterAddr", "127.0.0.1:8261",
                                "dmTaskName", "legacy-dm-task",
                                "max.batch.size", "8192"
                        )
                )
        );
    }

    private SyncJobDefinition oracleDefinition(Map<String, String> additionalProperties) {
        return new SyncJobDefinition(
                2201L,
                "oracle-to-tidb",
                SyncMode.INCREMENTAL_ONLY,
                DeploymentArchitecture.AMD64,
                new SourceConnectionProperties(
                        SourceDatabaseType.ORACLE,
                        "127.0.0.1",
                        1521,
                        "ORCLCDB",
                        "APP",
                        "system",
                        "oracle",
                        "jdbc:oracle:thin:@127.0.0.1:1521/ORCLCDB",
                        "",
                        null
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
                List.of(new TableMapping(
                        "",
                        "APP",
                        "ORDERS",
                        "target_db",
                        "orders",
                        List.of("ID"),
                        "UPDATED_AT",
                        List.of("ID", "UPDATED_AT"),
                        Map.of()
                )),
                new FullLoadConfig("sqluldr2", "./work/export", 1000, 1, Map.of()),
                new IncrementalConfig("sync_server", "", "", "./work/offsets/offset.dat", 5, 500, additionalProperties)
        );
    }

    private SyncJobDefinition mariaDbDefinition() {
        return new SyncJobDefinition(
                2601L,
                "mariadb-to-tidb",
                SyncMode.INCREMENTAL_ONLY,
                DeploymentArchitecture.AMD64,
                new SourceConnectionProperties(
                        SourceDatabaseType.MARIADB,
                        "127.0.0.1",
                        3306,
                        "sales",
                        "sales",
                        "root",
                        "root",
                        "jdbc:mariadb://127.0.0.1:3306/sales",
                        "",
                        null
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
                List.of(new TableMapping(
                        "sales",
                        "sales",
                        "orders",
                        "target_db",
                        "orders",
                        List.of("id"),
                        "updated_at",
                        List.of("id", "updated_at"),
                        Map.of()
                )),
                new FullLoadConfig("mariadb-dump", "./work/export", 1000, 1, Map.of()),
                new IncrementalConfig("sync_server", "", "", "./work/offsets/offset.dat", 5, 500, Map.of(
                        DebeziumChangeEventSource.MARIADB_SERVER_ID_ALIAS, "8601"
                ))
        );
    }

    private SyncJobDefinition sqlServerDefinition() {
        return new SyncJobDefinition(
                3201L,
                "sqlserver-to-tidb",
                SyncMode.INCREMENTAL_ONLY,
                DeploymentArchitecture.AMD64,
                new SourceConnectionProperties(
                        SourceDatabaseType.SQLSERVER,
                        "127.0.0.1",
                        1433,
                        "erp",
                        "dbo",
                        "sa",
                        "Password!",
                        "jdbc:sqlserver://127.0.0.1:1433;databaseName=erp",
                        "",
                        null
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
                List.of(new TableMapping(
                        "erp",
                        "dbo",
                        "orders",
                        "target_db",
                        "orders",
                        List.of("id"),
                        "updated_at",
                        List.of("id", "updated_at"),
                        Map.of()
                )),
                new FullLoadConfig("bcp", "./work/export", 1000, 1, Map.of()),
                new IncrementalConfig("sync_server", "", "", "./work/offsets/offset.dat", 5, 500, Map.of())
        );
    }

    private SyncJobDefinition db2Definition() {
        return new SyncJobDefinition(
                5201L,
                "db2-to-tidb",
                SyncMode.INCREMENTAL_ONLY,
                DeploymentArchitecture.AMD64,
                new SourceConnectionProperties(
                        SourceDatabaseType.DB2,
                        "127.0.0.1",
                        50000,
                        "SAMPLE",
                        "APP",
                        "db2inst1",
                        "Password1",
                        "jdbc:db2://127.0.0.1:50000/SAMPLE",
                        "",
                        null
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
                List.of(new TableMapping(
                        "",
                        "APP",
                        "ORDERS",
                        "target_db",
                        "orders",
                        List.of("ID"),
                        "UPDATED_AT",
                        List.of("ID", "UPDATED_AT"),
                        Map.of()
                )),
                new FullLoadConfig("db2", "./work/export", 1000, 1, Map.of()),
                new IncrementalConfig("sync_server", "", "", "./work/offsets/offset.dat", 5, 500, Map.of())
        );
    }

    private SyncJobDefinition postgresDefinition() {
        return new SyncJobDefinition(
                4201L,
                "postgres-to-tidb",
                SyncMode.INCREMENTAL_ONLY,
                DeploymentArchitecture.AMD64,
                new SourceConnectionProperties(
                        SourceDatabaseType.POSTGRESQL,
                        "127.0.0.1",
                        5432,
                        "inventory",
                        "public",
                        "postgres",
                        "postgres",
                        "jdbc:postgresql://127.0.0.1:5432/inventory",
                        "",
                        null
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
                List.of(new TableMapping(
                        "",
                        "public",
                        "orders",
                        "target_db",
                        "orders",
                        List.of("id"),
                        "updated_at",
                        List.of("id", "updated_at"),
                        Map.of()
                )),
                new FullLoadConfig("psql", "./work/export", 1000, 1, Map.of()),
                new IncrementalConfig(
                        "sync_server",
                        "sync_slot",
                        "sync_pub",
                        "./work/offsets/offset.dat",
                        5,
                        500,
                        Map.of(
                                DebeziumChangeEventSource.POSTGRES_PLUGIN_NAME_ALIAS, "pgoutput",
                                DebeziumChangeEventSource.POSTGRES_PUBLICATION_AUTO_CREATE_MODE_ALIAS, "filtered"
                        )
                )
        );
    }
}
