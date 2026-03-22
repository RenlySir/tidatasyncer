package com.example.sync.connectors.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.sync.core.config.TableMapping;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlIdentifierQuoterTest {

    @Test
    void shouldQuoteKnownSafeIdentifiers() {
        TableMapping mapping = new TableMapping(
                "source_db",
                "sales",
                "orders",
                "target_db",
                "orders",
                List.of("id"),
                "updated_at",
                List.of("id", "order_total"),
                Map.of()
        );

        assertEquals("`sales`.`orders`", SqlIdentifierQuoter.mysqlTable(mapping));
        assertEquals("\"id\", \"order_total\"", SqlIdentifierQuoter.postgresqlSelectList(mapping));
        assertEquals("[sales].[orders]", SqlIdentifierQuoter.sqlServerTable(mapping));
    }

    @Test
    void shouldRejectUnsafeIdentifiers() {
        TableMapping mapping = new TableMapping(
                "source_db",
                "sales",
                "orders;drop table users",
                "target_db",
                "orders",
                List.of("id"),
                "updated_at",
                List.of("id"),
                Map.of()
        );

        assertThrows(IllegalArgumentException.class, () -> SqlIdentifierQuoter.mysqlTable(mapping));
    }
}
