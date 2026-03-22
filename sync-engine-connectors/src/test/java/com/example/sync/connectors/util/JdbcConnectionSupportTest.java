package com.example.sync.connectors.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.TargetConnectionProperties;
import com.example.sync.core.model.SourceDatabaseType;
import org.junit.jupiter.api.Test;

class JdbcConnectionSupportTest {

    @Test
    void shouldBuildMySqlSourceUrlFromDiscreteFields() {
        SourceConnectionProperties source = new SourceConnectionProperties(
                SourceDatabaseType.MYSQL,
                "127.0.0.1",
                3306,
                "source_db",
                "source_db",
                "root",
                "root",
                "",
                "useUnicode=true&characterEncoding=utf8",
                null
        );

        assertEquals(
                "jdbc:mysql://127.0.0.1:3306/source_db?useUnicode=true&characterEncoding=utf8",
                JdbcConnectionSupport.resolveSourceJdbcUrl(source)
        );
    }

    @Test
    void shouldBuildSqlServerSourceUrlFromDiscreteFields() {
        SourceConnectionProperties source = new SourceConnectionProperties(
                SourceDatabaseType.SQLSERVER,
                "sqlserver.internal",
                1433,
                "appdb",
                "dbo",
                "sa",
                "Password1",
                "",
                "encrypt=false;trustServerCertificate=true",
                null
        );

        assertEquals(
                "jdbc:sqlserver://sqlserver.internal:1433;databaseName=appdb;encrypt=false;trustServerCertificate=true",
                JdbcConnectionSupport.resolveSourceJdbcUrl(source)
        );
    }

    @Test
    void shouldBuildMariaDbAndDb2SourceUrlsFromDiscreteFields() {
        SourceConnectionProperties mariaDb = new SourceConnectionProperties(
                SourceDatabaseType.MARIADB,
                "mariadb.internal",
                3306,
                "sales",
                "sales",
                "root",
                "root",
                "",
                "useUnicode=true&characterEncoding=utf8",
                null
        );
        assertEquals(
                "jdbc:mariadb://mariadb.internal:3306/sales?useUnicode=true&characterEncoding=utf8",
                JdbcConnectionSupport.resolveSourceJdbcUrl(mariaDb)
        );

        SourceConnectionProperties db2 = new SourceConnectionProperties(
                SourceDatabaseType.DB2,
                "db2.internal",
                50000,
                "SAMPLE",
                "APP",
                "db2inst1",
                "Password1",
                "",
                "currentSchema=APP;retrieveMessagesFromServerOnGetMessage=true",
                null
        );
        assertEquals(
                "jdbc:db2://db2.internal:50000/SAMPLE:currentSchema=APP;retrieveMessagesFromServerOnGetMessage=true",
                JdbcConnectionSupport.resolveSourceJdbcUrl(db2)
        );
    }

    @Test
    void shouldBuildTargetUrlFromDiscreteFields() {
        TargetConnectionProperties target = new TargetConnectionProperties(
                "127.0.0.1",
                4000,
                "target_db",
                "root",
                "",
                "",
                "useUnicode=true&characterEncoding=utf8&rewriteBatchedStatements=true",
                "tidb-lightning"
        );

        assertEquals(
                "jdbc:mysql://127.0.0.1:4000/target_db?useUnicode=true&characterEncoding=utf8&rewriteBatchedStatements=true",
                JdbcConnectionSupport.resolveTargetJdbcUrl(target)
        );
    }
}
