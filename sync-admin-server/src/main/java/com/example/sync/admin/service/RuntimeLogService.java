package com.example.sync.admin.service;

import com.example.sync.admin.dto.RuntimeLogFileResponse;
import com.example.sync.admin.dto.RuntimeLogTailResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RuntimeLogService {

    private final Path logDirectory;
    private final String applicationName;

    public RuntimeLogService(
            @Value("${logging.file.path:./logs}") String logDirectory,
            @Value("${spring.application.name:sync-admin-server}") String applicationName
    ) {
        this.logDirectory = Path.of(logDirectory).toAbsolutePath().normalize();
        this.applicationName = applicationName;
    }

    public List<RuntimeLogFileResponse> listFiles() {
        return logFileDefinitions().entrySet().stream()
                .map(entry -> toFileResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(RuntimeLogFileResponse::key))
                .toList();
    }

    public RuntimeLogTailResponse tail(String key, int lines) throws IOException {
        if (lines <= 0) {
            throw new IllegalArgumentException("lines must be greater than 0");
        }
        LogFileDefinition definition = logFileDefinitions().get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported runtime log file: " + key);
        }

        Path file = definition.path();
        if (!Files.exists(file)) {
            return new RuntimeLogTailResponse(
                    key,
                    definition.displayName(),
                    file.toString(),
                    false,
                    0L,
                    null,
                    0,
                    List.of()
            );
        }

        ArrayDeque<String> buffer = new ArrayDeque<>(lines);
        try (Stream<String> stream = Files.lines(file)) {
            stream.forEach(line -> {
                if (buffer.size() == lines) {
                    buffer.removeFirst();
                }
                buffer.addLast(line);
            });
        }

        return new RuntimeLogTailResponse(
                key,
                definition.displayName(),
                file.toString(),
                true,
                Files.size(file),
                Files.getLastModifiedTime(file).toInstant(),
                buffer.size(),
                List.copyOf(buffer)
        );
    }

    private RuntimeLogFileResponse toFileResponse(String key, LogFileDefinition definition) {
        Path file = definition.path();
        boolean exists = Files.exists(file);
        try {
            return new RuntimeLogFileResponse(
                    key,
                    definition.displayName(),
                    file.toString(),
                    exists,
                    exists ? Files.size(file) : 0L,
                    exists ? Files.getLastModifiedTime(file).toInstant() : null
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect log file: " + file, ex);
        }
    }

    private Map<String, LogFileDefinition> logFileDefinitions() {
        return Map.of(
                "app", new LogFileDefinition("Application Log", logDirectory.resolve(applicationName + ".log")),
                "error", new LogFileDefinition("Error Log", logDirectory.resolve(applicationName + "-error.log"))
        );
    }

    private record LogFileDefinition(String displayName, Path path) {
    }
}
