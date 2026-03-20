package com.example.sync.core.spi;

import java.nio.file.Path;
import java.util.List;

public record CsvExportResult(
        Path exportDirectory,
        List<Path> csvFiles,
        long exportedRows
) {
}
