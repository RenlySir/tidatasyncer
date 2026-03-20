package com.example.sync.core.spi;

import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.runtime.StandardChangeEvent;

public interface ChangeEventSink {

    void open(SyncJobDefinition definition, ProgressReporter reporter) throws Exception;

    void accept(StandardChangeEvent event) throws Exception;

    void close() throws Exception;
}
