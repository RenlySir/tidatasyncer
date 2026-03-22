package com.example.sync.admin.dto;

public record CompatibilityFindingResponse(
        String category,
        String objectType,
        String objectName,
        String compatibility,
        String severity,
        String message,
        String suggestion
) {
}
