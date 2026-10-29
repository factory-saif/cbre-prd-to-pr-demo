package com.example.facilities.workorder;

import com.example.facilities.workorder.api.CreateWorkOrderRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkOrderService {

    static final Duration REOPEN_WINDOW = Duration.ofDays(7);

    private static final Comparator<WorkOrder> DISPLAY_ORDER = Comparator
            .comparing((WorkOrder order) -> order.status().isClosed())
            .thenComparing(WorkOrder::dueAt)
            .thenComparing(WorkOrder::id);

    private final WorkOrderRepository repository;
    private final Clock clock;

    public WorkOrderService(WorkOrderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public WorkOrder create(CreateWorkOrderRequest request) {
        return createAt(request, clock.instant());
    }

    synchronized WorkOrder createAt(CreateWorkOrderRequest request, Instant createdAt) {
        WorkOrder order = new WorkOrder(
                repository.nextId(),
                request.title().trim(),
                trimToNull(request.description()),
                request.location().trim(),
                request.category(),
                request.priority(),
                request.requester().trim(),
                createdAt);
        return repository.save(order);
    }

    public List<WorkOrder> list(WorkOrderFilter filter) {
        Instant now = clock.instant();
        return repository.findAll().stream()
                .filter(order -> filter.matches(order, now))
                .sorted(DISPLAY_ORDER)
                .toList();
    }

    public WorkOrder get(String id) {
        return repository.findById(id).orElseThrow(() -> new WorkOrderNotFoundException(id));
    }

    // Synchronized so the timestamp is read inside the monitor; otherwise a
    // concurrent mutation could record a later time before this one is applied.
    public synchronized WorkOrder assign(String id, String assignee) {
        return assignAt(id, assignee, clock.instant());
    }

    synchronized WorkOrder assignAt(String id, String assignee, Instant at) {
        WorkOrder order = get(id);
        if (order.status().isClosed()) {
            throw new InvalidTransitionException(
                    id + " is " + order.status().displayName().toLowerCase() + " and cannot be reassigned");
        }
        order.assignTo(assignee.trim(), at);
        return order;
    }

    public synchronized WorkOrder changeStatus(String id, Status next, String note) {
        return changeStatusAt(id, next, note, clock.instant());
    }

    synchronized WorkOrder changeStatusAt(String id, Status next, String note, Instant at) {
        WorkOrder order = get(id);
        if (next == Status.ASSIGNED) {
            throw new InvalidTransitionException("Use the assign action to assign " + id);
        }
        if (!order.status().canTransitionTo(next)) {
            throw new InvalidTransitionException("Cannot move " + id + " from "
                    + order.status().displayName() + " to " + next.displayName());
        }
        order.moveTo(next, at, trimToNull(note));
        return order;
    }

    public synchronized WorkOrder reopen(String id, String reason) {
        WorkOrder order = get(id);
        if (order.status() != Status.COMPLETED) {
            throw new InvalidTransitionException("Cannot reopen " + id + ": only completed work orders can be reopened");
        }
        Instant now = clock.instant();
        if (now.isAfter(order.completedAt().plus(REOPEN_WINDOW))) {
            throw new InvalidTransitionException("Cannot reopen " + id
                    + ": it was completed more than " + REOPEN_WINDOW.toDays() + " days ago");
        }
        order.reopen(now, reason.trim());
        return order;
    }

    public boolean isOverdue(WorkOrder order) {
        return order.isOverdueAt(clock.instant());
    }

    public boolean isAtRisk(WorkOrder order) {
        return order.isAtRiskAt(clock.instant());
    }

    public WorkOrderSummary summary() {
        Instant now = clock.instant();
        List<WorkOrder> all = repository.findAll();
        Map<Status, Long> byStatus = new EnumMap<>(Status.class);
        for (Status status : Status.values()) {
            byStatus.put(status, 0L);
        }
        all.forEach(order -> byStatus.merge(order.status(), 1L, Long::sum));
        long active = all.stream().filter(order -> !order.status().isClosed()).count();
        long overdue = all.stream().filter(order -> order.isOverdueAt(now)).count();
        long atRisk = all.stream().filter(order -> order.isAtRiskAt(now)).count();
        long urgentActive = all.stream()
                .filter(order -> !order.status().isClosed() && order.priority() == Priority.URGENT)
                .count();
        return new WorkOrderSummary(all.size(), active, overdue, atRisk, urgentActive, byStatus);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
