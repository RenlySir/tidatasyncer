package com.example.sync.connectors.export;

import com.example.sync.connectors.util.CsvDirectoryPreprocessor;
import com.example.sync.connectors.util.PreparedCsvDirectory;
import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.model.JobPhase;
import com.example.sync.core.model.SourceDatabaseType;
import com.example.sync.core.spi.CsvExportResult;
import com.example.sync.core.spi.FullLoadExporter;
import com.example.sync.core.spi.ProgressReporter;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class CsvDirectoryExporter implements FullLoadExporter {

    private final CsvDirectoryPreprocessor csvDirectoryPreprocessor;

    public CsvDirectoryExporter(CsvDirectoryPreprocessor csvDirectoryPreprocessor) {
        this.csvDirectoryPreprocessor = csvDirectoryPreprocessor;
    }

    @Override
    public boolean supports(SyncJobDefinition definition) {
        return definition.source().databaseType() == SourceDatabaseType.CSV;
    }

    @Override
    public CsvExportResult export(SyncJobDefinition definition, ProgressReporter reporter) throws Exception {
        Path directory = Path.of(definition.fullLoad().exportBaseDir()).toAbsolutePath().normalize();
        reporter.updatePhase(JobPhase.EXPORTING_FULL, 5, "Checking CSV directory charset and file size");
        PreparedCsvDirectory prepared = csvDirectoryPreprocessor.prepare(directory);
        reporter.updateFullLoadMetrics(
                prepared.totalCsvFiles(),
                prepared.totalCsvFiles(),
                prepared.csvFiles().stream().mapToLong(this::size).sum(),
                "CSV directory prepared: converted " + prepared.convertedCharsetFiles()
                        + " files, split " + prepared.splitSourceFiles() + " oversized files"
        );
        reporter.updatePhase(JobPhase.EXPORTING_FULL, 100, "CSV directory prepared for TiDB Lightning");
        return new CsvExportResult(directory, prepared.csvFiles(), 0L);
    }

    private long size(Path path) {
        try {
            return java.nio.file.Files.size(path);
        } catch (Exception ex) {
            return 0L;
        }
    }
}
