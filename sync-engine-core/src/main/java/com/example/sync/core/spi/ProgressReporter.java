package com.example.sync.core.spi;

import com.example.sync.core.model.JobPhase;
import com.example.sync.core.runtime.StandardChangeEvent;

public interface ProgressReporter {

    void updatePhase(JobPhase phase, int percent, String message);

    void updateLag(long lagMillis, String message);

    void updateLatestEvent(StandardChangeEvent event);

    void log(String level, String message);

    boolean isStopRequested();
}
