package com.example.sync.connectors.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class MongoDocumentMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MongoDocumentMapper() {
    }

    public static Map<String, Object> toFlatMap(Document document, List<String> includedFields) {
        if (document == null) {
            return Map.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (includedFields != null && !includedFields.isEmpty()) {
            for (String field : includedFields) {
                Object value = extractByPath(document, field);
                result.put(field, sanitizeValue(value));
            }
            return result;
        }

        for (Map.Entry<String, Object> entry : document.entrySet()) {
            result.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return result;
    }

    static Object extractByPath(Map<String, Object> source, String path) {
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    static Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof Document document) {
            return document.toJson();
        }
        if (value instanceof List<?> list) {
            try {
                return OBJECT_MAPPER.writeValueAsString(list);
            } catch (Exception ex) {
                return list.toString();
            }
        }
        if (value instanceof Map<?, ?> map) {
            try {
                return OBJECT_MAPPER.writeValueAsString(map);
            } catch (Exception ex) {
                return map.toString();
            }
        }
        return value;
    }
}
