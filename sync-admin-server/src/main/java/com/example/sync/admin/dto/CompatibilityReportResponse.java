package com.example.sync.admin.dto;

import com.example.sync.admin.domain.CompatibilityReportStatus;
import java.time.Instant;
import java.util.List;

public record CompatibilityReportResponse(
        Long id,
        String name,
        Long sourceProfileId,
        Long targetProfileId,
        CompatibilityReportStatus status,
        String lastMessage,
        CompatibilitySummaryResponse summary,
        List<CompatibilityFindingResponse> findings,
        String reportMarkdown,
        String reportHtml,
        String reportPath,
        String reportHtmlPath,
        Instant executedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
