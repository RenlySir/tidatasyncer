package com.example.sync.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeLogServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void tailShouldReturnLastRequestedLines() throws Exception {
        Path appLog = tempDir.resolve("sync-admin-server.log");
        Files.writeString(appLog, "line1\nline2\nline3\nline4\n", StandardCharsets.UTF_8);
        RuntimeLogService service = new RuntimeLogService(tempDir.toString(), "sync-admin-server");

        var response = service.tail("app", 2);

        assertThat(response.exists()).isTrue();
        assertThat(response.lineCount()).isEqualTo(2);
        assertThat(response.lines()).containsExactly("line3", "line4");
    }

    @Test
    void listFilesShouldExposeKnownRuntimeLogs() {
        RuntimeLogService service = new RuntimeLogService(tempDir.toString(), "sync-admin-server");

        var files = service.listFiles();

        assertThat(files).extracting("key").containsExactlyInAnyOrder("app", "error");
        assertThat(files).allMatch(file -> file.absolutePath().contains("sync-admin-server"));
    }
}
