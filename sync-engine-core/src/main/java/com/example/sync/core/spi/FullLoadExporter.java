package com.example.sync.core.spi;

import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.spi.ProgressReporter;

public interface FullLoadExporter {

    boolean supports(SyncJobDefinition definition);

    CsvExportResult export(SyncJobDefinition definition, ProgressReporter reporter) throws Exception;
}
