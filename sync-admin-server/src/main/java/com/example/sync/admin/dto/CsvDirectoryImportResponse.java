package com.example.sync.admin.dto;

public record CsvDirectoryImportResponse(
        String directoryPath,
        int importedCsvFiles,
        String message
) {
}
