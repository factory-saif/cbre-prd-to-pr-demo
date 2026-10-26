package com.example.facilities.workorder;

import com.example.facilities.workorder.api.CreateWorkOrderRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkOrderService {

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

    public synchronized WorkOrder assign(String id, String assignee) {
        WorkOrder order = get(id);
        if (order.status().isClosed()) {
            throw new InvalidTransitionException(
                    id + " is " + order.status().displayName().toLowerCase() + " and cannot be reassigned");
        }
        order.assignTo(assignee.trim(), clock.instant());
        return order;
    }

    public synchronized WorkOrder changeStatus(String id, Status next, String note) {
        WorkOrder order = get(id);
        if (next == Status.ASSIGNED) {
            throw new InvalidTransitionException("Use the assign action to assign " + id);
        }
        if (!order.status().canTransitionTo(next)) {
            throw new InvalidTransitionException("Cannot move " + id + " from "
                    + order.status().displayName() + " to " + next.displayName());
        }
        order.moveTo(next, clock.instant(), trimToNull(note));
        return order;
    }

    public boolean isOverdue(WorkOrder order) {
        return order.isOverdueAt(clock.instant());
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
        long urgentActive = all.stream()
                .filter(order -> !order.status().isClosed() && order.priority() == Priority.URGENT)
                .count();
        return new WorkOrderSummary(all.size(), active, overdue, urgentActive, byStatus);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
