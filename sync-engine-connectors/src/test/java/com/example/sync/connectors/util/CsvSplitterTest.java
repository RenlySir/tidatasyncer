package com.example.sync.connectors.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvSplitterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRenameSingleFileToLightningConvention() throws Exception {
        Path sourceFile = tempDir.resolve("raw.csv");
        Files.writeString(sourceFile, "id,name\n1,Alice\n2,Bob\n", StandardCharsets.UTF_8);

        List<Path> files = CsvSplitter.splitForLightning(sourceFile, tempDir, "target_db", "orders", 1024 * 1024);

        assertEquals(1, files.size());
        assertEquals("target_db.orders.00000001.csv", files.get(0).getFileName().toString());
        assertTrue(Files.exists(files.get(0)));
    }

    @Test
    void shouldSplitFileAndUseNumberedLightningNames() throws Exception {
        Path sourceFile = tempDir.resolve("raw.csv");
        StringBuilder builder = new StringBuilder("id,name\n");
        for (int i = 0; i < 20; i++) {
            builder.append(i).append(",").append("x".repeat(20)).append("\n");
        }
        Files.writeString(sourceFile, builder.toString(), StandardCharsets.UTF_8);

        List<Path> files = CsvSplitter.splitForLightning(sourceFile, tempDir, "target_db", "orders", 64);

        assertTrue(files.size() > 1);
        assertEquals("target_db.orders.00000001.csv", files.get(0).getFileName().toString());
        assertEquals("target_db.orders.00000002.csv", files.get(1).getFileName().toString());
        for (Path file : files) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            assertEquals("id,name", lines.get(0));
        }
    }
}
