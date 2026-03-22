package com.example.sync.connectors.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sync.core.config.TableMapping;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TiDbJdbcChangeEventSinkTest {

    private final TiDbJdbcChangeEventSink sink = new TiDbJdbcChangeEventSink();

    @Test
    void shouldBuildQuotedUpsertSqlWithDeterministicColumnOrder() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "DONE");
        payload.put("id", 7L);

        TiDbJdbcChangeEventSink.SqlStatement statement = sink.buildUpsertStatement(mapping(), payload);

        assertEquals(
                "INSERT INTO `target_db`.`orders` (`id`,`order_status`) VALUES (?,?) ON DUPLICATE KEY UPDATE `id`=VALUES(`id`),`order_status`=VALUES(`order_status`)",
                statement.sql()
        );
        assertIterableEquals(List.of(7L, "DONE"), statement.args());
    }

    @Test
    void shouldBuildDeleteSqlUsingPrimaryKeysFirst() {
        TiDbJdbcChangeEventSink.SqlStatement statement = sink.buildDeleteStatement(mapping(), Map.of("id", 11L, "status", "IGNORED"));

        assertEquals("DELETE FROM `target_db`.`orders` WHERE `id`=?", statement.sql());
        assertIterableEquals(List.of(11L), statement.args());
    }

    @Test
    void shouldDetectIdentityChangeFromPrimaryKeys() {
        assertTrue(sink.hasIdentityChange(mapping(), Map.of("id", 1L), Map.of("id", 2L)));
        assertFalse(sink.hasIdentityChange(mapping(), Map.of("id", 1L), Map.of("id", 1L, "status", "DONE")));
    }

    @Test
    void shouldExtractPrimaryKeyValuesFromBeforeImage() {
        Map<String, Object> keys = sink.extractPrimaryKeyValues(mapping(), Map.of("id", 5L, "status", "RUNNING"));

        assertEquals(Map.of("id", 5L), keys);
    }

    private TableMapping mapping() {
        return new TableMapping(
                "source_db",
                "source_db",
                "orders",
                "target_db",
                "orders",
                List.of("id"),
                "updated_at",
                List.of("id", "status"),
                Map.of("status", "order_status")
        );
    }
}
