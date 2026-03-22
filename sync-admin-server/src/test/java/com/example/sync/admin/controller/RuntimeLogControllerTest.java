package com.example.sync.admin.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sync.admin.dto.RuntimeLogFileResponse;
import com.example.sync.admin.dto.RuntimeLogTailResponse;
import com.example.sync.admin.service.RuntimeLogService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RuntimeLogController.class)
@Import(com.example.sync.admin.web.GlobalExceptionHandler.class)
class RuntimeLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RuntimeLogService runtimeLogService;

    @Test
    void listFilesShouldReturnConfiguredLogFiles() throws Exception {
        when(runtimeLogService.listFiles()).thenReturn(List.of(
                new RuntimeLogFileResponse("app", "Application Log", "/tmp/app.log", true, 1024L, Instant.parse("2026-03-21T12:00:00Z"))
        ));

        mockMvc.perform(get("/api/runtime-logs/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("app"))
                .andExpect(jsonPath("$[0].absolutePath").value("/tmp/app.log"));
    }

    @Test
    void tailShouldReturnRuntimeLogContent() throws Exception {
        when(runtimeLogService.tail("error", 100)).thenReturn(new RuntimeLogTailResponse(
                "error",
                "Error Log",
                "/tmp/error.log",
                true,
                2048L,
                Instant.parse("2026-03-21T12:00:00Z"),
                2,
                List.of("line-a", "line-b")
        ));

        mockMvc.perform(get("/api/runtime-logs/tail").param("key", "error").param("lines", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("error"))
                .andExpect(jsonPath("$.lines[1]").value("line-b"));
    }
}
