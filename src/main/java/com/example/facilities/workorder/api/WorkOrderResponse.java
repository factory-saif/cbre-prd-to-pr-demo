package com.example.facilities.workorder.api;

import com.example.facilities.workorder.ActivityEntry;
import com.example.facilities.workorder.Category;
import com.example.facilities.workorder.Priority;
import com.example.facilities.workorder.Status;
import com.example.facilities.workorder.WorkOrder;
import java.time.Instant;
import java.util.List;

public record WorkOrderResponse(
        String id,
        String title,
        String description,
        String location,
        Category category,
        Priority priority,
        Status status,
        String requester,
        String assignee,
        Instant createdAt,
        Instant dueAt,
        Instant updatedAt,
        Instant completedAt,
        boolean overdue,
        boolean atRisk,
        boolean reopened,
        int reopenCount,
        Instant reopenedAt,
        List<Status> nextStatuses,
        List<ActivityEntry> activity) {

    public static WorkOrderResponse from(WorkOrder order, boolean overdue, boolean atRisk) {
        return new WorkOrderResponse(
                order.id(),
                order.title(),
                order.description(),
                order.location(),
                order.category(),
                order.priority(),
                order.status(),
                order.requester(),
                order.assignee(),
                order.createdAt(),
                order.dueAt(),
                order.updatedAt(),
                order.completedAt(),
                overdue,
                atRisk,
                order.isReopened(),
                order.reopenCount(),
                order.reopenedAt(),
                order.status().nextStatuses(),
                order.activity());
    }
}
