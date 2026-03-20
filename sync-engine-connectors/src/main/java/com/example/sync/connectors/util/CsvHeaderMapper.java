package com.example.sync.connectors.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CsvHeaderMapper {

    private CsvHeaderMapper() {
    }

    public static void rewriteHeader(Path csvFile, Map<String, String> columnMappings) throws IOException {
        if (columnMappings == null || columnMappings.isEmpty()) {
            return;
        }

        List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return;
        }

        String[] columns = lines.get(0).split(",", -1);
        List<String> remapped = new ArrayList<>(columns.length);
        for (String column : columns) {
            remapped.add(columnMappings.getOrDefault(column, column));
        }
        lines.set(0, String.join(",", remapped));
        Files.write(csvFile, lines, StandardCharsets.UTF_8);
    }
}
