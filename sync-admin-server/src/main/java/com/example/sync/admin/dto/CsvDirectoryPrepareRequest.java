package com.example.sync.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CsvDirectoryPrepareRequest(
        @NotBlank String directoryPath
) {
}
