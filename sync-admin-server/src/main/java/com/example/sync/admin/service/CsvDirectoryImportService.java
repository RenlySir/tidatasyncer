package com.example.sync.admin.service;

import com.example.sync.admin.dto.CsvDirectoryImportRequest;
import com.example.sync.admin.dto.CsvDirectoryImportResponse;
import com.example.sync.admin.dto.CsvDirectoryPrepareRequest;
import com.example.sync.admin.dto.CsvDirectoryPrepareResponse;
import com.example.sync.connectors.importer.TiDbLightningImporter;
import com.example.sync.connectors.util.CsvDirectoryPreprocessor;
import com.example.sync.connectors.util.PreparedCsvDirectory;
import com.example.sync.core.config.FullLoadConfig;
import com.example.sync.core.config.IncrementalConfig;
import com.example.sync.core.config.SourceConnectionProperties;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.model.SyncMode;
import com.example.sync.core.spi.CsvExportResult;
import com.example.sync.core.spi.ProgressReporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CsvDirectoryImportService {

    private final TiDbLightningImporter lightningImporter;
    private final CsvDirectoryPreprocessor csvDirectoryPreprocessor;

    public CsvDirectoryImportService(
            TiDbLightningImporter lightningImporter,
            CsvDirectoryPreprocessor csvDirectoryPreprocessor
    ) {
        this.lightningImporter = lightningImporter;
        this.csvDirectoryPreprocessor = csvDirectoryPreprocessor;
    }

    public CsvDirectoryPrepareResponse prepare(CsvDirectoryPrepareRequest request) throws IOException {
        Path directory = validateDirectory(request.directoryPath());
        return toResponse(csvDirectoryPreprocessor.prepare(directory));
    }

    CsvDirectoryPrepareResponse prepare(Path directory, long splitTriggerSizeBytes, long chunkSizeBytes) throws IOException {
        return toResponse(csvDirectoryPreprocessor.prepare(directory, splitTriggerSizeBytes, chunkSizeBytes));
    }

    public CsvDirectoryImportResponse importDirectory(CsvDirectoryImportRequest request) throws Exception {
        Path directory = validateDirectory(request.directoryPath());
        List<Path> preparedFiles = csvDirectoryPreprocessor.listPreparedCsvFiles(directory);

        SyncJobDefinition definition = buildImportDefinition(request);
        lightningImporter.importCsv(
                definition,
                new CsvExportResult(directory, preparedFiles, 0L),
                new DirectoryImportProgressReporter()
        );
        return new CsvDirectoryImportResponse(
                directory.toString(),
                preparedFiles.size(),
                "TiDB Lightning import completed"
        );
    }

    private SyncJobDefinition buildImportDefinition(CsvDirectoryImportRequest request) {
        return new SyncJobDefinition(
                0L,
                "csv-directory-import",
                SyncMode.FULL_ONLY,
                request.deploymentArchitecture(),
                new SourceConnectionProperties(
                        SourceDatabaseType.CSV,
                        "",
                        0,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        ""
                ),
                request.target(),
                List.of(),
                new FullLoadConfig("", request.directoryPath(), 0, 1, Map.of()),
                new IncrementalConfig("", "", "", "", 0, 0, Map.of())
        );
    }

    private Path validateDirectory(String directoryPath) throws IOException {
        Path directory = Path.of(directoryPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Directory not found: " + directory);
        }
        return directory;
    }

    private CsvDirectoryPrepareResponse toResponse(PreparedCsvDirectory prepared) {
        List<String> fileNames = prepared.csvFiles().stream()
                .map(path -> path.getFileName().toString())
                .toList();
        return new CsvDirectoryPrepareResponse(
                prepared.directoryPath().toString(),
                prepared.totalCsvFiles(),
                prepared.convertedCharsetFiles(),
                prepared.splitSourceFiles(),
                prepared.generatedChunkFiles(),
                prepared.unchangedFiles(),
                fileNames,
                prepared.convertedCharsetFiles() > 0 || prepared.splitSourceFiles() > 0
                        ? "CSV directory prepared successfully. Non-UTF8 files were converted to UTF-8 and files larger than 200 MiB were split into 128 MiB chunks."
                        : "CSV directory prepared successfully. All files are already UTF-8 and below 200 MiB."
        );
    }

    private static final class DirectoryImportProgressReporter implements ProgressReporter {

        @Override
        public void updatePhase(com.example.sync.core.model.JobPhase phase, int percent, String message) {
        }

        @Override
        public void updateLag(long lagMillis, String message) {
        }

        @Override
        public void updateLatestEvent(com.example.sync.core.runtime.StandardChangeEvent event) {
        }

        @Override
        public void log(String level, String message) {
        }

        @Override
        public boolean isStopRequested() {
            return false;
        }
    }
}
