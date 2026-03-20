package com.example.sync.core.spi;

import com.example.sync.core.config.SyncJobDefinition;

public interface FullLoadImporter {

    void importCsv(SyncJobDefinition definition, CsvExportResult exportResult, ProgressReporter reporter) throws Exception;
}
