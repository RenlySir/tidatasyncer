package com.example.sync.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sync.admin.domain.SyncJobStatus;
import com.example.sync.admin.dto.SyncJobDefinitionResponse;
import com.example.sync.admin.dto.SyncJobResponse;
import com.example.sync.admin.service.SyncJobRuntimeManager;
import com.example.sync.admin.service.SyncJobService;
import com.example.sync.admin.support.SyncJobFixtures;
import com.example.sync.core.model.JobPhase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SyncJobController.class)
@Import(com.example.sync.admin.config.AsyncExecutionConfig.class)
class SyncJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SyncJobService syncJobService;

    @MockBean
    private SyncJobRuntimeManager runtimeManager;

    @Test
    void createShouldReturnPersistedJob() throws Exception {
        SyncJobResponse response = new SyncJobResponse(
                1L,
                "mysql-to-tidb",
                SyncJobFixtures.jobDefinition().syncMode(),
                SyncJobStatus.DRAFT,
                JobPhase.CREATED,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(syncJobService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(SyncJobFixtures.upsertRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("mysql-to-tidb"));
    }

    @Test
    void getDefinitionShouldReturnSerializedDefinition() throws Exception {
        when(syncJobService.getDefinition(1L)).thenReturn(
                new SyncJobDefinitionResponse(1L, "mysql-to-tidb", SyncJobFixtures.jobDefinition())
        );

        mockMvc.perform(get("/api/jobs/1/definition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.definition.source.databaseType").value("MYSQL"))
                .andExpect(jsonPath("$.definition.tableMappings[0].targetTable").value("orders"));
    }

    @Test
    void updateShouldDelegateToService() throws Exception {
        SyncJobResponse response = new SyncJobResponse(
                1L,
                "mysql-to-tidb",
                SyncJobFixtures.jobDefinition().syncMode(),
                SyncJobStatus.DRAFT,
                JobPhase.CREATED,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(syncJobService.update(eq(1L), any())).thenReturn(response);

        mockMvc.perform(put("/api/jobs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(SyncJobFixtures.upsertRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("mysql-to-tidb"));

        verify(syncJobService).update(eq(1L), any());
    }

    @Test
    void startShouldDelegateToRuntimeManager() throws Exception {
        mockMvc.perform(post("/api/jobs/1/start"))
                .andExpect(status().isOk());

        verify(runtimeManager).start(1L);
    }
}
