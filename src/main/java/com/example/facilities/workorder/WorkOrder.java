package com.example.facilities.workorder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class WorkOrder {

    private final String id;
    private final String title;
    private final String description;
    private final String location;
    private final Category category;
    private final Priority priority;
    private final String requester;
    private final Instant createdAt;
    private final List<ActivityEntry> activity = new ArrayList<>();

    private Status status;
    private String assignee;
    private Instant updatedAt;
    private Instant completedAt;
    private Instant dueAt;
    private Instant windowStart;
    private int reopenCount;
    private Instant reopenedAt;

    WorkOrder(String id, String title, String description, String location, Category category,
              Priority priority, String requester, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.location = location;
        this.category = category;
        this.priority = priority;
        this.requester = requester;
        this.createdAt = createdAt;
        this.windowStart = createdAt;
        this.dueAt = createdAt.plus(priority.responseTarget());
        this.status = Status.OPEN;
        this.updatedAt = createdAt;
        this.activity.add(new ActivityEntry(createdAt, "Request created by " + requester));
    }

    void assignTo(String newAssignee, Instant at) {
        String message = assignee == null
                ? "Assigned to " + newAssignee
                : "Reassigned from " + assignee + " to " + newAssignee;
        assignee = newAssignee;
        if (status == Status.OPEN) {
            status = Status.ASSIGNED;
        }
        touch(at, message);
    }

    void moveTo(Status next, Instant at, String note) {
        String message = "Status changed from " + status.displayName() + " to " + next.displayName();
        if (note != null) {
            message += ": " + note;
        }
        status = next;
        if (next == Status.COMPLETED) {
            completedAt = at;
        }
        touch(at, message);
    }

    void reopen(Instant at, String reason) {
        String message = "Status changed from " + status.displayName() + " to "
                + Status.IN_PROGRESS.displayName() + ": Reopened: " + reason;
        status = Status.IN_PROGRESS;
        completedAt = null;
        reopenedAt = at;
        windowStart = at;
        dueAt = at.plus(priority.responseTarget());
        reopenCount++;
        touch(at, message);
    }

    public boolean isOverdueAt(Instant now) {
        return !status.isClosed() && now.isAfter(dueAt);
    }

    public boolean isAtRiskAt(Instant now) {
        Instant atRiskFrom = windowStart.plus(priority.responseTarget().multipliedBy(3).dividedBy(4));
        return !status.isClosed() && !now.isBefore(atRiskFrom) && !now.isAfter(dueAt);
    }

    private void touch(Instant at, String message) {
        updatedAt = at;
        activity.add(new ActivityEntry(at, message));
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String location() {
        return location;
    }

    public Category category() {
        return category;
    }

    public Priority priority() {
        return priority;
    }

    public String requester() {
        return requester;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant dueAt() {
        return dueAt;
    }

    public Status status() {
        return status;
    }

    public String assignee() {
        return assignee;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public int reopenCount() {
        return reopenCount;
    }

    public Instant reopenedAt() {
        return reopenedAt;
    }

    public boolean isReopened() {
        return reopenCount > 0;
    }

    public List<ActivityEntry> activity() {
        return List.copyOf(activity);
    }
}
