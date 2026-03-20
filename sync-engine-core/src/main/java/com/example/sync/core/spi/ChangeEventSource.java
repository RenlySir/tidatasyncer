package com.example.sync.core.spi;

import com.example.sync.core.config.SyncJobDefinition;

public interface ChangeEventSource {

    boolean supports(SyncJobDefinition definition);

    ChangeCaptureHandle start(SyncJobDefinition definition, ChangeEventSink sink, ProgressReporter reporter) throws Exception;
}
