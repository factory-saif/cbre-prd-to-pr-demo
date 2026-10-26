package com.example.facilities.workorder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Repository;

@Repository
public class WorkOrderRepository {

    private final Map<String, WorkOrder> orders = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(1000);

    String nextId() {
        return "WO-" + sequence.incrementAndGet();
    }

    WorkOrder save(WorkOrder order) {
        orders.put(order.id(), order);
        return order;
    }

    Optional<WorkOrder> findById(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    List<WorkOrder> findAll() {
        return List.copyOf(orders.values());
    }
}
