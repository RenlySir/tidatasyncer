package com.example.sync.admin.service;

import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.runtime.StandardChangeEvent;
import com.example.sync.core.spi.ChangeEventSink;
import com.example.sync.core.spi.ProgressReporter;

public class NoOpChangeEventSink implements ChangeEventSink {

    @Override
    public void open(SyncJobDefinition definition, ProgressReporter reporter) {
    }

    @Override
    public void accept(StandardChangeEvent event) {
    }

    @Override
    public void close() {
    }
}
