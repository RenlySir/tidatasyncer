package com.example.sync.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.sync.admin.domain.ConnectionProfileEntity;
import com.example.sync.admin.domain.ConnectionProfileRole;
import com.example.sync.admin.domain.DatabaseEndpointType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class ConnectionProfilePermissionCheckServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCheckCsvDirectoryPermissions() throws Exception {
        Path csvDir = Files.createDirectory(tempDir.resolve("csv-source"));
        ConnectionProfileEntity entity = new ConnectionProfileEntity();
        entity.setId(1L);
        entity.setName("csv-source");
        entity.setRole(ConnectionProfileRole.SOURCE);
        entity.setDatabaseType(DatabaseEndpointType.CSV);
        entity.setCsvDirectory(csvDir.toString());

        ConnectionProfileService connectionProfileService = Mockito.mock(ConnectionProfileService.class);
        when(connectionProfileService.findEntity(1L)).thenReturn(entity);

        ConnectionProfilePermissionCheckService service = new ConnectionProfilePermissionCheckService(
                connectionProfileService,
                new ConnectionProfileBindingService()
        );

        var response = service.check(1L);

        assertThat(response.passed()).isTrue();
        assertThat(response.missingPermissions()).isEmpty();
        assertThat(response.checks()).extracting("passed").containsOnly(true);
    }
}
