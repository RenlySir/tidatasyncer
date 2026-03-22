package com.example.sync.admin.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sync.admin.domain.ConnectionProfileRole;
import com.example.sync.admin.domain.DatabaseEndpointType;
import com.example.sync.admin.dto.ConnectionProfilePermissionCheckItemResponse;
import com.example.sync.admin.dto.ConnectionProfilePermissionCheckResponse;
import com.example.sync.admin.service.ConnectionProfilePermissionCheckService;
import com.example.sync.admin.service.ConnectionProfileService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConnectionProfileController.class)
class ConnectionProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConnectionProfileService connectionProfileService;

    @MockBean
    private ConnectionProfilePermissionCheckService permissionCheckService;

    @Test
    void shouldReturnPermissionCheckResult() throws Exception {
        when(permissionCheckService.check(1L)).thenReturn(new ConnectionProfilePermissionCheckResponse(
                1L,
                "mysql-source",
                ConnectionProfileRole.SOURCE,
                DatabaseEndpointType.MYSQL,
                false,
                "Permission check failed. Grant the missing privileges and run the check again.",
                List.of("REPLICATION CLIENT"),
                List.of("GRANT REPLICATION CLIENT ON *.* TO 'sync_user'@'%';"),
                List.of(new ConnectionProfilePermissionCheckItemResponse("binlog", "Read binlog", false, "REPLICATION CLIENT privilege is missing.")),
                Instant.parse("2026-03-22T00:00:00Z")
        ));

        mockMvc.perform(post("/api/connection-profiles/1/permission-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(1))
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.missingPermissions[0]").value("REPLICATION CLIENT"))
                .andExpect(jsonPath("$.suggestedGrantStatements[0]").value("GRANT REPLICATION CLIENT ON *.* TO 'sync_user'@'%';"));
    }
}
