package com.example.facilities.workorder;

import com.example.facilities.workorder.api.CreateWorkOrderRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "facilities.demo-data", havingValue = "true", matchIfMissing = true)
class DemoDataLoader implements ApplicationRunner {

    private final WorkOrderService service;
    private final Clock clock;

    DemoDataLoader(WorkOrderService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        Instant now = clock.instant();

        WorkOrder hvac = seed(now.minus(Duration.ofHours(6)), "HVAC not cooling in the east wing",
                "Tenants report the space is above 80°F since the morning.", "Tower A · Floor 12",
                Category.HVAC, Priority.URGENT, "Morgan Ellis");
        service.assign(hvac.id(), "Jordan Lee");

        seed(now.minus(Duration.ofHours(30)), "Flickering lights in conference room 4B", null,
                "Tower A · Floor 4", Category.ELECTRICAL, Priority.HIGH, "Casey Nguyen");

        WorkOrder leak = seed(now.minus(Duration.ofHours(2)), "Sink leaking in the north restroom",
                "Water is pooling under the second sink.", "Tower B · Floor 3",
                Category.PLUMBING, Priority.HIGH, "Riley Patel");
        service.assign(leak.id(), "Priya Shah");
        service.changeStatus(leak.id(), Status.IN_PROGRESS, "Shut-off valve closed, parts ordered");

        seed(now.minus(Duration.ofHours(1)), "Badge reader offline at the loading dock", null,
                "Tower B · Dock 2", Category.SECURITY, Priority.URGENT, "Taylor Brooks");

        seed(now.minus(Duration.ofHours(3).plusMinutes(15)), "Freight elevator door sticking", null,
                "Tower B · Freight elevator", Category.GENERAL, Priority.URGENT, "Casey Nguyen");

        WorkOrder cleaning = seed(now.minus(Duration.ofHours(20)), "Deep clean after the town hall",
                "Event space needs a full reset before Friday.", "Tower A · Floor 1 Event Hall",
                Category.CLEANING, Priority.MEDIUM, "Jamie Cruz");
        service.assign(cleaning.id(), "Sam Rivera");

        WorkOrder tile = seed(now.minus(Duration.ofDays(3)), "Replace stained ceiling tile in the lobby", null,
                "Tower A · Lobby", Category.GENERAL, Priority.LOW, "Morgan Ellis");
        service.assign(tile.id(), "Alex Kim");
        service.changeStatus(tile.id(), Status.IN_PROGRESS, null);
        service.changeStatus(tile.id(), Status.COMPLETED, "Tile replaced");
    }

    private WorkOrder seed(Instant createdAt, String title, String description, String location,
                           Category category, Priority priority, String requester) {
        return service.createAt(
                new CreateWorkOrderRequest(title, description, location, category, priority, requester),
                createdAt);
    }
}
