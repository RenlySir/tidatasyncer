package com.example.sync.core.spi;

import com.example.sync.core.model.JobPhase;
import com.example.sync.core.runtime.StandardChangeEvent;

public interface ProgressReporter {

    void updatePhase(JobPhase phase, int percent, String message);

    void updateLag(long lagMillis, String message);

    void updateLatestEvent(StandardChangeEvent event);

    default void updateFullLoadMetrics(int exportedTableCount, int totalTableCount, long exportedBytes, String message) {
    }

    default void updateImportMetrics(int importedTableCount, int totalTableCount, long importedBytes, String message) {
    }

    default void updateLogPosition(String logPosition, String message) {
    }

    void log(String level, String message);

    boolean isStopRequested();
}
