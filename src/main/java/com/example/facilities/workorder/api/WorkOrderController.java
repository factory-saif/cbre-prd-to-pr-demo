package com.example.facilities.workorder.api;

import com.example.facilities.workorder.Category;
import com.example.facilities.workorder.Priority;
import com.example.facilities.workorder.Status;
import com.example.facilities.workorder.WorkOrder;
import com.example.facilities.workorder.WorkOrderFilter;
import com.example.facilities.workorder.WorkOrderService;
import com.example.facilities.workorder.WorkOrderSummary;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkOrderResponse> list(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(name = "q", required = false) String query) {
        WorkOrderFilter filter = new WorkOrderFilter(status, priority, category, overdue, query);
        return service.list(filter).stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<WorkOrderResponse> create(@Valid @RequestBody CreateWorkOrderRequest request,
                                                    UriComponentsBuilder uriBuilder) {
        WorkOrder created = service.create(request);
        URI location = uriBuilder.path("/api/work-orders/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(toResponse(created));
    }

    @GetMapping("/summary")
    public WorkOrderSummary summary() {
        return service.summary();
    }

    @GetMapping("/{id}")
    public WorkOrderResponse get(@PathVariable String id) {
        return toResponse(service.get(id));
    }

    @PostMapping("/{id}/assign")
    public WorkOrderResponse assign(@PathVariable String id, @Valid @RequestBody AssignRequest request) {
        return toResponse(service.assign(id, request.assignee()));
    }

    @PostMapping("/{id}/status")
    public WorkOrderResponse changeStatus(@PathVariable String id, @Valid @RequestBody StatusChangeRequest request) {
        return toResponse(service.changeStatus(id, request.status(), request.note()));
    }

    private WorkOrderResponse toResponse(WorkOrder order) {
        return WorkOrderResponse.from(order, service.isOverdue(order));
    }
}
