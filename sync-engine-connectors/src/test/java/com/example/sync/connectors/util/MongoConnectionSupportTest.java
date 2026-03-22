package com.example.sync.connectors.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.model.SourceDatabaseType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MongoConnectionSupportTest {

    @Test
    void shouldPreferExplicitMongoConnectionString() {
        SourceConnectionProperties source = new SourceConnectionProperties(
                SourceDatabaseType.MONGODB,
                "127.0.0.1",
                27017,
                "source_db",
                null,
                "user",
                "pass",
                "mongodb://user:pass@mongo:27017/source_db?authSource=admin",
                "",
                null
        );

        String result = MongoConnectionSupport.resolveConnectionString(source, Map.of("authSource", "ignored"));

        assertEquals("mongodb://user:pass@mongo:27017/source_db?authSource=admin", result);
    }

    @Test
    void shouldBuildConnectionStringFromHostPortAndExtraProperties() {
        SourceConnectionProperties source = new SourceConnectionProperties(
                SourceDatabaseType.MONGODB,
                "mongo.internal",
                27017,
                "source_db",
                null,
                "user",
                "pass",
                null,
                "authSource=admin&replicaSet=rs0",
                null
        );

        String result = MongoConnectionSupport.resolveConnectionString(source, Map.of(
                "authSource", "admin",
                "replicaSet", "rs0"
        ));

        assertTrue(result.startsWith("mongodb://user:pass@mongo.internal:27017/source_db"));
        assertTrue(result.contains("authSource=admin"));
        assertTrue(result.contains("replicaSet=rs0"));
    }
}
