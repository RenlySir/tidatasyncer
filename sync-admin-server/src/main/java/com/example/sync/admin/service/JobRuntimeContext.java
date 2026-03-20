package com.example.sync.admin.service;

import com.example.sync.core.spi.ChangeCaptureHandle;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class JobRuntimeContext {

    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private Future<?> future;
    private ChangeCaptureHandle captureHandle;

    public AtomicBoolean getStopRequested() {
        return stopRequested;
    }

    public Future<?> getFuture() {
        return future;
    }

    public void setFuture(Future<?> future) {
        this.future = future;
    }

    public ChangeCaptureHandle getCaptureHandle() {
        return captureHandle;
    }

    public void setCaptureHandle(ChangeCaptureHandle captureHandle) {
        this.captureHandle = captureHandle;
    }
}
