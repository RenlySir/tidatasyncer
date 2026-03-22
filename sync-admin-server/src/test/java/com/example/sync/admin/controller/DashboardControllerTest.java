package com.example.sync.admin.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sync.admin.domain.CompatibilityReportStatus;
import com.example.sync.admin.domain.ConnectionProfileRole;
import com.example.sync.admin.domain.DatabaseEndpointType;
import com.example.sync.admin.domain.SchemaSyncTaskStatus;
import com.example.sync.admin.domain.SyncJobStatus;
import com.example.sync.admin.dto.CompatibilityFindingResponse;
import com.example.sync.admin.dto.CompatibilityReportResponse;
import com.example.sync.admin.dto.CompatibilitySummaryResponse;
import com.example.sync.admin.dto.ConnectionProfileResponse;
import com.example.sync.admin.dto.SchemaSyncTaskResponse;
import com.example.sync.admin.dto.SyncJobResponse;
import com.example.sync.admin.service.CompatibilityReportService;
import com.example.sync.admin.service.ConnectionProfileService;
import com.example.sync.admin.service.SchemaSyncTaskService;
import com.example.sync.admin.service.SyncJobService;
import com.example.sync.admin.service.ToolConfigService;
import com.example.sync.core.model.JobPhase;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.model.SyncMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConnectionProfileService connectionProfileService;

    @MockBean
    private ToolConfigService toolConfigService;

    @MockBean
    private CompatibilityReportService compatibilityReportService;

    @MockBean
    private SchemaSyncTaskService schemaSyncTaskService;

    @MockBean
    private SyncJobService syncJobService;

    @Test
    void overviewShouldReturnPipelineSummary() throws Exception {
        when(connectionProfileService.list(ConnectionProfileRole.SOURCE)).thenReturn(List.of(
                new ConnectionProfileResponse(
                        1L,
                        "oracle-source",
                        ConnectionProfileRole.SOURCE,
                        DatabaseEndpointType.ORACLE,
                        "127.0.0.1",
                        1521,
                        "orcl",
                        "APP",
                        "sync",
                        "secret",
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2026-03-21T00:00:00Z"),
                        Instant.parse("2026-03-22T00:00:00Z")
                ),
                new ConnectionProfileResponse(
                        2L,
                        "csv-dropzone",
                        ConnectionProfileRole.SOURCE,
                        DatabaseEndpointType.CSV,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "/tmp/csv",
                        null,
                        null,
                        Instant.parse("2026-03-20T00:00:00Z"),
                        Instant.parse("2026-03-22T00:00:00Z")
                )
        ));
        when(connectionProfileService.list(ConnectionProfileRole.TARGET)).thenReturn(List.of(
                new ConnectionProfileResponse(
                        3L,
                        "tidb-target",
                        ConnectionProfileRole.TARGET,
                        DatabaseEndpointType.TIDB,
                        "127.0.0.1",
                        4000,
                        "tidb_sync",
                        null,
                        "root",
                        "secret",
                        null,
                        null,
                        null,
                        null,
                        10080,
                        Instant.parse("2026-03-21T00:00:00Z"),
                        Instant.parse("2026-03-22T00:00:00Z")
                )
        ));
        when(toolConfigService.list()).thenReturn(List.of());
        when(compatibilityReportService.list()).thenReturn(List.of(
                new CompatibilityReportResponse(
                        1L,
                        "oracle-report",
                        1L,
                        3L,
                        CompatibilityReportStatus.COMPLETED,
                        "completed",
                        new CompatibilitySummaryResponse(12, 2, 1, 0, 0, 2, 8, 20, 1, 1, 0, 0, 0),
                        List.of(new CompatibilityFindingResponse("OBJECT", "VIEW", "APP.V_TEST", "PARTIAL", "WARN", "View review required", "Rewrite view for TiDB")),
                        "# report",
                        "<html></html>",
                        "/tmp/report.md",
                        "/tmp/report.html",
                        Instant.parse("2026-03-22T01:00:00Z"),
                        Instant.parse("2026-03-21T00:00:00Z"),
                        Instant.parse("2026-03-22T01:00:00Z")
                )
        ));
        when(schemaSyncTaskService.list()).thenReturn(List.of(
                new SchemaSyncTaskResponse(
                        1L,
                        "oracle-schema",
                        1L,
                        3L,
                        "DATABASE_ALL",
                        List.of(),
                        Map.of(),
                        SchemaSyncTaskStatus.COMPLETED,
                        "done",
                        "CREATE TABLE t (id bigint)",
                        "/tmp/schema.sql",
                        null,
                        List.of(),
                        Instant.parse("2026-03-22T02:00:00Z"),
                        Instant.parse("2026-03-21T00:00:00Z"),
                        Instant.parse("2026-03-22T02:00:00Z")
                )
        ));
        when(syncJobService.list()).thenReturn(List.of(
                new SyncJobResponse(
                        101L,
                        "oracle-hybrid",
                        SourceDatabaseType.ORACLE,
                        SyncMode.FULL_AND_INCREMENTAL,
                        SyncJobStatus.RUNNING,
                        JobPhase.RUNNING_INCREMENTAL,
                        68,
                        "running",
                        null,
                        1200L,
                        10,
                        12,
                        1024L,
                        12,
                        2048L,
                        "scn:123",
                        "orcl",
                        "APP",
                        "T_ORDER",
                        "ID=1",
                        Instant.parse("2026-03-21T00:00:00Z"),
                        Instant.parse("2026-03-22T03:00:00Z"),
                        Instant.parse("2026-03-22T02:30:00Z"),
                        null
                ),
                new SyncJobResponse(
                        102L,
                        "mysql-full",
                        SourceDatabaseType.MYSQL,
                        SyncMode.FULL_ONLY,
                        SyncJobStatus.COMPLETED,
                        JobPhase.COMPLETED,
                        100,
                        "completed",
                        null,
                        null,
                        8,
                        8,
                        4096L,
                        8,
                        4096L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2026-03-20T00:00:00Z"),
                        Instant.parse("2026-03-22T02:00:00Z"),
                        Instant.parse("2026-03-22T01:00:00Z"),
                        Instant.parse("2026-03-22T02:00:00Z")
                )
        ));

        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceProfileCount").value(2))
                .andExpect(jsonPath("$.targetProfileCount").value(1))
                .andExpect(jsonPath("$.csvSourceCount").value(1))
                .andExpect(jsonPath("$.completedCompatibilityReportCount").value(1))
                .andExpect(jsonPath("$.completedSchemaTaskCount").value(1))
                .andExpect(jsonPath("$.batchEnabledJobCount").value(2))
                .andExpect(jsonPath("$.realtimeEnabledJobCount").value(1))
                .andExpect(jsonPath("$.fullAndIncrementalJobCount").value(1))
                .andExpect(jsonPath("$.pipelineReadinessScore").value(80))
                .andExpect(jsonPath("$.recentJobs[0].name").value("oracle-hybrid"));
    }
}
