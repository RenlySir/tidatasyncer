package com.example.sync.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sync.admin.domain.DatabaseEndpointType;
import java.sql.Types;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaTypeMappingServiceTest {

    private final SchemaTypeMappingService service = new SchemaTypeMappingService();

    @Test
    void oracleVarchar2ShouldMapToVarchar() {
        SchemaTypeMappingService.ColumnMappingResult result = service.mapColumn(
                DatabaseEndpointType.ORACLE,
                "ORDERS",
                new SchemaTypeMappingService.SourceColumnDefinition("CUSTOMER_NAME", "VARCHAR2", Types.VARCHAR, 128, 0, true),
                Map.of()
        );

        assertThat(result.targetType()).isEqualTo("VARCHAR(128)");
        assertThat(result.unsupportedItem()).isNull();
    }

    @Test
    void oracleLargeNumberShouldMapToDecimal() {
        SchemaTypeMappingService.ColumnMappingResult result = service.mapColumn(
                DatabaseEndpointType.ORACLE,
                "ORDERS",
                new SchemaTypeMappingService.SourceColumnDefinition("AMOUNT", "NUMBER", Types.NUMERIC, 24, 0, true),
                Map.of()
        );

        assertThat(result.targetType()).isEqualTo("DECIMAL(24,0)");
    }

    @Test
    void oracleXmlTypeShouldBeReportedAsUnsupported() {
        SchemaTypeMappingService.ColumnMappingResult result = service.mapColumn(
                DatabaseEndpointType.ORACLE,
                "ORDERS",
                new SchemaTypeMappingService.SourceColumnDefinition("PAYLOAD", "XMLTYPE", Types.SQLXML, 0, 0, true),
                Map.of()
        );

        assertThat(result.targetType()).isEqualTo("JSON");
        assertThat(result.unsupportedItem()).isNotNull();
        assertThat(result.unsupportedItem().reason()).contains("XMLTYPE");
    }

    @Test
    void oracleTimestampWithTimeZoneShouldRequireManualReview() {
        SchemaTypeMappingService.ColumnMappingResult result = service.mapColumn(
                DatabaseEndpointType.ORACLE,
                "ORDERS",
                new SchemaTypeMappingService.SourceColumnDefinition("EVENT_TIME", "TIMESTAMP WITH TIME ZONE", Types.TIMESTAMP_WITH_TIMEZONE, 0, 6, true),
                Map.of()
        );

        assertThat(result.targetType()).isEqualTo("VARCHAR(64)");
        assertThat(result.unsupportedItem()).isNotNull();
    }

    @Test
    void mysqlTimestampShouldKeepTemporalType() {
        SchemaTypeMappingService.ColumnMappingResult result = service.mapColumn(
                DatabaseEndpointType.MYSQL,
                "ORDERS",
                new SchemaTypeMappingService.SourceColumnDefinition("UPDATED_AT", "TIMESTAMP", Types.TIMESTAMP, 0, 6, true),
                Map.of()
        );

        assertThat(result.targetType()).isEqualTo("TIMESTAMP(6)");
    }

    @Test
    void mysqlUnsignedIntegerShouldPreserveUnsignedSemantics() {
        SchemaTypeMappingService.ColumnMappingResult result = service.mapColumn(
                DatabaseEndpointType.MYSQL,
                "ORDERS",
                new SchemaTypeMappingService.SourceColumnDefinition("ID", "INT UNSIGNED", Types.INTEGER, 10, 0, false),
                Map.of()
        );

        assertThat(result.targetType()).isEqualTo("INT UNSIGNED");
    }

    @Test
    void sqlServerUniqueIdentifierShouldMapToChar36() {
        SchemaTypeMappingService.ColumnMappingResult result = service.mapColumn(
                DatabaseEndpointType.SQLSERVER,
                "ORDERS",
                new SchemaTypeMappingService.SourceColumnDefinition("ROW_GUID", "UNIQUEIDENTIFIER", Types.OTHER, 36, 0, true),
                Map.of()
        );

        assertThat(result.targetType()).isEqualTo("CHAR(36)");
        assertThat(result.unsupportedItem()).isNull();
    }

    @Test
    void sqlServerDateTimeOffsetShouldRequireReview() {
        SchemaTypeMappingService.ColumnMappingResult result = service.mapColumn(
                DatabaseEndpointType.SQLSERVER,
                "ORDERS",
                new SchemaTypeMappingService.SourceColumnDefinition("EVENT_TIME", "DATETIMEOFFSET", Types.TIMESTAMP_WITH_TIMEZONE, 0, 7, true),
                Map.of()
        );

        assertThat(result.targetType()).isEqualTo("DATETIME(6)");
        assertThat(result.unsupportedItem()).isNotNull();
    }

    @Test
    void postgreSqlJsonbShouldMapToJson() {
        SchemaTypeMappingService.ColumnMappingResult result = service.mapColumn(
                DatabaseEndpointType.POSTGRESQL,
                "orders",
                new SchemaTypeMappingService.SourceColumnDefinition("payload", "JSONB", Types.OTHER, 0, 0, true),
                Map.of()
        );

        assertThat(result.targetType()).isEqualTo("JSON");
    }

    @Test
    void postgreSqlTimestampWithTimeZoneShouldRequireManualReview() {
        SchemaTypeMappingService.ColumnMappingResult result = service.mapColumn(
                DatabaseEndpointType.POSTGRESQL,
                "orders",
                new SchemaTypeMappingService.SourceColumnDefinition("created_at", "TIMESTAMPTZ", Types.TIMESTAMP_WITH_TIMEZONE, 0, 6, true),
                Map.of()
        );

        assertThat(result.targetType()).isEqualTo("VARCHAR(64)");
        assertThat(result.unsupportedItem()).isNotNull();
    }

    @Test
    void overrideShouldTakePriority() {
        SchemaTypeMappingService.ColumnMappingResult result = service.mapColumn(
                DatabaseEndpointType.ORACLE,
                "ORDERS",
                new SchemaTypeMappingService.SourceColumnDefinition("ID", "NUMBER", Types.NUMERIC, 24, 0, false),
                Map.of("orders.id", "VARCHAR(64)")
        );

        assertThat(result.targetType()).isEqualTo("VARCHAR(64)");
        assertThat(result.unsupportedItem()).isNull();
    }
}
