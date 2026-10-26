package com.example.facilities.workorder;

import java.time.Duration;

public enum Priority {
    URGENT(Duration.ofHours(4)),
    HIGH(Duration.ofHours(24)),
    MEDIUM(Duration.ofHours(72)),
    LOW(Duration.ofDays(7));

    private final Duration responseTarget;

    Priority(Duration responseTarget) {
        this.responseTarget = responseTarget;
    }

    public Duration responseTarget() {
        return responseTarget;
    }
}
