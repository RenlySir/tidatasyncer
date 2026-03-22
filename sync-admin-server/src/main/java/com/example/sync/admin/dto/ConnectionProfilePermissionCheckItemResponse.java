package com.example.sync.admin.dto;

public record ConnectionProfilePermissionCheckItemResponse(
        String key,
        String label,
        boolean passed,
        String detail
) {
}
