package com.example.sync.admin.dto;

public record ManagedToolPathsResponse(
        String tidbLightningBinary,
        String dumplingBinary,
        String sqluldr2Binary,
        String bcpBinary,
        String sqlcmdBinary
) {
}
