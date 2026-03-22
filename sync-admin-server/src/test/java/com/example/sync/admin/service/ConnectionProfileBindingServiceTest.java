package com.example.sync.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.sync.admin.domain.ConnectionProfileEntity;
import com.example.sync.admin.domain.ConnectionProfileRole;
import com.example.sync.admin.domain.DatabaseEndpointType;
import org.junit.jupiter.api.Test;

class ConnectionProfileBindingServiceTest {

    private final ConnectionProfileBindingService service = new ConnectionProfileBindingService();

    @Test
    void shouldResolveJdbcPropertiesAndMetadataScope() {
        ConnectionProfileEntity source = new ConnectionProfileEntity();
        source.setRole(ConnectionProfileRole.SOURCE);
        source.setDatabaseType(DatabaseEndpointType.ORACLE);
        source.setHost("10.0.0.8");
        source.setPort(1521);
        source.setDatabaseName("ORCLCDB");
        source.setSchemaName("app");
        source.setUsername("sync_user");
        source.setPassword("secret");
        source.setJdbcParameters("oracle.net.CONNECT_TIMEOUT=3000");

        var validated = service.requireSourceProfile(source, "schema synchronization", false);
        var properties = service.toSourceProperties(validated);

        assertThat(properties.databaseType()).isEqualTo(com.example.sync.core.model.SourceDatabaseType.ORACLE);
        assertThat(properties.host()).isEqualTo("10.0.0.8");
        assertThat(properties.port()).isEqualTo(1521);
        assertThat(service.tableCatalog(source)).isNull();
        assertThat(service.tableSchema(source)).isEqualTo("APP");
        assertThat(service.oracleOwner(source)).isEqualTo("APP");
    }

    @Test
    void shouldUseDatabaseSpecificDefaultsForSchemaAndTarget() {
        ConnectionProfileEntity postgres = new ConnectionProfileEntity();
        postgres.setRole(ConnectionProfileRole.SOURCE);
        postgres.setDatabaseType(DatabaseEndpointType.POSTGRESQL);
        postgres.setUsername("pg_user");

        ConnectionProfileEntity tidb = new ConnectionProfileEntity();
        tidb.setRole(ConnectionProfileRole.TARGET);
        tidb.setDatabaseType(DatabaseEndpointType.TIDB);
        tidb.setHost("127.0.0.1");
        tidb.setPort(4000);
        tidb.setDatabaseName("target_db");
        tidb.setUsername("root");
        tidb.setPassword("pwd");

        assertThat(service.tableSchema(postgres)).isEqualTo("public");
        assertThat(service.toTargetProperties(tidb, "/opt/tidb-lightning").statusPort()).isEqualTo(10080);
    }

    @Test
    void shouldRejectUnsupportedRoleOrTargetType() {
        ConnectionProfileEntity csv = new ConnectionProfileEntity();
        csv.setRole(ConnectionProfileRole.SOURCE);
        csv.setDatabaseType(DatabaseEndpointType.CSV);

        ConnectionProfileEntity mysqlTarget = new ConnectionProfileEntity();
        mysqlTarget.setRole(ConnectionProfileRole.TARGET);
        mysqlTarget.setDatabaseType(DatabaseEndpointType.MYSQL);

        assertThatThrownBy(() -> service.requireSourceProfile(csv, "compatibility report scanning", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CSV source does not support compatibility report scanning");

        assertThatThrownBy(() -> service.requireTidbTargetProfile(mysqlTarget, "Compatibility report"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Compatibility report target must be TiDB");
    }
}
