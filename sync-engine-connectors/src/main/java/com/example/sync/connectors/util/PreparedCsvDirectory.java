package com.example.sync.connectors.util;

import java.nio.file.Path;
import java.util.List;

public record PreparedCsvDirectory(
        Path directoryPath,
        int totalCsvFiles,
        int convertedCharsetFiles,
        int splitSourceFiles,
        int generatedChunkFiles,
        int unchangedFiles,
        List<Path> csvFiles
) {
}
