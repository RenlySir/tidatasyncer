package com.example.sync.admin.service;

import com.example.sync.core.config.SyncJobDefinition;
import com.example.sync.core.runtime.StandardChangeEvent;
import com.example.sync.core.spi.ChangeEventSink;
import com.example.sync.core.spi.ProgressReporter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BufferingChangeEventSink implements ChangeEventSink {

    private final ChangeEventSink delegate;
    private final Queue<StandardChangeEvent> buffer = new ConcurrentLinkedQueue<>();
    private volatile boolean buffering = true;

    public BufferingChangeEventSink(ChangeEventSink delegate) {
        this.delegate = delegate;
    }

    @Override
    public void open(SyncJobDefinition definition, ProgressReporter reporter) throws Exception {
        delegate.open(definition, reporter);
    }

    @Override
    public synchronized void accept(StandardChangeEvent event) throws Exception {
        if (buffering) {
            buffer.offer(event);
            return;
        }
        delegate.accept(event);
    }

    public synchronized void replayAndSwitch(ProgressReporter reporter) throws Exception {
        reporter.log("INFO", "Replaying buffered CDC events");
        StandardChangeEvent event;
        while ((event = buffer.poll()) != null) {
            delegate.accept(event);
        }
        buffering = false;
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }
}
