package com.example.sync.core.spi;

public interface ChangeCaptureHandle extends AutoCloseable {

    boolean isRunning();

    void awaitStop() throws InterruptedException;

    @Override
    void close() throws Exception;
}
