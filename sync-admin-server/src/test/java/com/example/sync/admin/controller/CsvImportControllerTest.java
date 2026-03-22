package com.example.sync.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sync.admin.dto.CsvDirectoryImportResponse;
import com.example.sync.admin.dto.CsvDirectoryPrepareResponse;
import com.example.sync.admin.service.CsvDirectoryImportService;
import com.example.sync.core.config.TargetConnectionProperties;
import com.example.sync.core.model.DeploymentArchitecture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CsvImportController.class)
class CsvImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CsvDirectoryImportService csvDirectoryImportService;

    @Test
    void prepareShouldReturnDirectoryPreparationResult() throws Exception {
        when(csvDirectoryImportService.prepare(any())).thenReturn(new CsvDirectoryPrepareResponse(
                "/data/csv",
                3,
                1,
                1,
                2,
                1,
                java.util.List.of("target_db.orders.00000001.csv", "target_db.orders.00000002.csv"),
                "ok"
        ));

        mockMvc.perform(post("/api/csv-import/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(java.util.Map.of("directoryPath", "/data/csv"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directoryPath").value("/data/csv"))
                .andExpect(jsonPath("$.splitSourceFiles").value(1));

        verify(csvDirectoryImportService).prepare(any());
    }

    @Test
    void startShouldReturnImportResult() throws Exception {
        when(csvDirectoryImportService.importDirectory(any())).thenReturn(new CsvDirectoryImportResponse(
                "/data/csv",
                2,
                "done"
        ));

        mockMvc.perform(post("/api/csv-import/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(java.util.Map.of(
                                "directoryPath", "/data/csv",
                                "deploymentArchitecture", DeploymentArchitecture.AMD64.name(),
                                "target", new TargetConnectionProperties(
                                        "127.0.0.1",
                                        4000,
                                        "target_db",
                                        "root",
                                        "root",
                                        "jdbc:mysql://127.0.0.1:4000/target_db",
                                        "",
                                        "tidb-lightning"
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCsvFiles").value(2))
                .andExpect(jsonPath("$.message").value("done"));

        verify(csvDirectoryImportService).importDirectory(any());
    }
}
