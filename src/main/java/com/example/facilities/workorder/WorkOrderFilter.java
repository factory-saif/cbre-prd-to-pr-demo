package com.example.facilities.workorder;

import java.time.Instant;
import java.util.Locale;
import java.util.stream.Stream;

public record WorkOrderFilter(Status status, Priority priority, Category category, Boolean overdue, Boolean atRisk,
                              String query) {

    public static WorkOrderFilter none() {
        return new WorkOrderFilter(null, null, null, null, null, null);
    }

    boolean matches(WorkOrder order, Instant now) {
        if (status != null && order.status() != status) {
            return false;
        }
        if (priority != null && order.priority() != priority) {
            return false;
        }
        if (category != null && order.category() != category) {
            return false;
        }
        if (overdue != null && order.isOverdueAt(now) != overdue) {
            return false;
        }
        if (atRisk != null && order.isAtRiskAt(now) != atRisk) {
            return false;
        }
        return matchesQuery(order);
    }

    private boolean matchesQuery(WorkOrder order) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return Stream.of(order.id(), order.title(), order.location(), order.requester(), order.assignee())
                .anyMatch(value -> value != null && value.toLowerCase(Locale.ROOT).contains(needle));
    }
}
