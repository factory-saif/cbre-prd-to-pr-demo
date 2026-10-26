package com.example.facilities.workorder;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum Status {
    OPEN("Open"),
    ASSIGNED("Assigned"),
    IN_PROGRESS("In progress"),
    ON_HOLD("On hold"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private static final Map<Status, Set<Status>> ALLOWED = new EnumMap<>(Status.class);

    static {
        ALLOWED.put(OPEN, EnumSet.of(ASSIGNED, CANCELLED));
        ALLOWED.put(ASSIGNED, EnumSet.of(IN_PROGRESS, CANCELLED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(ON_HOLD, COMPLETED, CANCELLED));
        ALLOWED.put(ON_HOLD, EnumSet.of(IN_PROGRESS, CANCELLED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(Status.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(Status.class));
    }

    private final String displayName;

    Status(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isClosed() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean canTransitionTo(Status next) {
        return ALLOWED.get(this).contains(next);
    }

    public List<Status> nextStatuses() {
        return List.copyOf(ALLOWED.get(this));
    }
}
