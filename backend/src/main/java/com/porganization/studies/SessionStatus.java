package com.porganization.studies;

public enum SessionStatus {
    RUNNING,
    PAUSED,
    FINISHED,
    ABANDONED;

    public boolean isActive() {
        return this == RUNNING || this == PAUSED;
    }
}
