package com.example.sync.admin.dto;

import com.example.sync.admin.domain.ConnectionProfileRole;
import com.example.sync.admin.domain.DatabaseEndpointType;
import java.time.Instant;
import java.util.List;

public record ConnectionProfilePermissionCheckResponse(
        Long profileId,
        String profileName,
        ConnectionProfileRole role,
        DatabaseEndpointType databaseType,
        boolean passed,
        String summary,
        List<String> missingPermissions,
        List<String> suggestedGrantStatements,
        List<ConnectionProfilePermissionCheckItemResponse> checks,
        Instant checkedAt
) {
}
