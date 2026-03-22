package com.example.sync.connectors.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class MongoDocumentMapperTest {

    @Test
    void shouldExtractIncludedFieldsUsingDotPath() {
        Document document = new Document("_id", new ObjectId("507f1f77bcf86cd799439011"))
                .append("name", "Alice")
                .append("address", new Document("city", "Shanghai").append("zip", "200000"))
                .append("tags", List.of("vip", "trial"));

        Map<String, Object> mapped = MongoDocumentMapper.toFlatMap(
                document,
                List.of("_id", "name", "address.city", "tags")
        );

        assertEquals("507f1f77bcf86cd799439011", mapped.get("_id"));
        assertEquals("Alice", mapped.get("name"));
        assertEquals("Shanghai", mapped.get("address.city"));
        assertEquals("[\"vip\",\"trial\"]", mapped.get("tags"));
    }
}
