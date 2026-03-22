package com.example.sync.admin.dto;

import java.util.List;

public record CsvDirectoryPrepareResponse(
        String directoryPath,
        int totalCsvFiles,
        int convertedCharsetFiles,
        int splitSourceFiles,
        int generatedChunkFiles,
        int unchangedFiles,
        List<String> csvFiles,
        String message
) {
}
