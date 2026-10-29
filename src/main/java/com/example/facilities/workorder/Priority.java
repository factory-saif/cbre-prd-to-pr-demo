package com.example.facilities.workorder;

import java.time.Duration;

public enum Priority {
    URGENT("Urgent", Duration.ofHours(4)),
    HIGH("High", Duration.ofHours(24)),
    MEDIUM("Medium", Duration.ofHours(72)),
    LOW("Low", Duration.ofDays(7));

    private final String displayName;
    private final Duration responseTarget;

    Priority(String displayName, Duration responseTarget) {
        this.displayName = displayName;
        this.responseTarget = responseTarget;
    }

    public String displayName() {
        return displayName;
    }

    public Duration responseTarget() {
        return responseTarget;
    }
}
