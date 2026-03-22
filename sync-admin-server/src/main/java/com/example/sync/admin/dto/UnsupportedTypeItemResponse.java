package com.example.sync.admin.dto;

public record UnsupportedTypeItemResponse(
        String tableName,
        String columnName,
        String sourceType,
        String suggestedTargetType,
        String reason
) {
}
