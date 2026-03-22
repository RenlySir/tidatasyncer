package com.example.sync.connectors.util;

import com.example.sync.core.spi.ChangeCaptureHandle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleChangeCaptureHandle implements ChangeCaptureHandle {

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ExecutorService executorService;
    private final Future<?> future;
    private final Runnable shutdownAction;
    private final CountDownLatch stopLatch;

    public SimpleChangeCaptureHandle(
            ExecutorService executorService,
            Future<?> future,
            Runnable shutdownAction,
            CountDownLatch stopLatch
    ) {
        this.executorService = executorService;
        this.future = future;
        this.shutdownAction = shutdownAction;
        this.stopLatch = stopLatch;
    }

    @Override
    public boolean isRunning() {
        return running.get() && !future.isDone();
    }

    @Override
    public void awaitStop() throws InterruptedException {
        stopLatch.await();
        if (future.isCancelled()) {
            return;
        }
        try {
            future.get(100, TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Change capture stopped with error", ex.getCause());
        } catch (java.util.concurrent.TimeoutException ignored) {
        }
    }

    @Override
    public void close() throws Exception {
        if (running.compareAndSet(true, false)) {
            shutdownAction.run();
            future.cancel(true);
            executorService.shutdownNow();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
