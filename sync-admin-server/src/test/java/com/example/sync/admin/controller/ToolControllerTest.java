package com.example.sync.admin.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sync.admin.service.SystemSettingsService;
import com.example.sync.core.model.DeploymentArchitecture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ToolController.class)
class ToolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemSettingsService systemSettingsService;

    @Test
    void shouldReturnManagedTidbLightningPathForArchitecture() throws Exception {
        when(systemSettingsService.getDeploymentArchitecture()).thenReturn(DeploymentArchitecture.AMD64);
        mockMvc.perform(get("/api/tools/managed-paths").param("architecture", "ARM64"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tidbLightningBinary").value(org.hamcrest.Matchers.containsString("/vendor/tools/arm/tidb-lightning/tidb-lightning")))
                .andExpect(jsonPath("$.bcpBinary").value(org.hamcrest.Matchers.containsString("/vendor/tools/arm/bcp/bcp")))
                .andExpect(jsonPath("$.sqlcmdBinary").value(org.hamcrest.Matchers.containsString("/vendor/tools/arm/sqlcmd/sqlcmd")));
    }
}
