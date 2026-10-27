package com.example.facilities.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.facilities.workorder.api.CreateWorkOrderRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorkOrderServiceTest {

    private static final Instant START = Instant.parse("2026-03-02T09:00:00Z");

    private MutableClock clock;
    private WorkOrderService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        service = new WorkOrderService(new WorkOrderRepository(), clock);
    }

    @ParameterizedTest
    @CsvSource({"URGENT, 4", "HIGH, 24", "MEDIUM, 72", "LOW, 168"})
    void newWorkOrderIsOpenAndDueWithinItsPriorityResponseTarget(Priority priority, long hours) {
        WorkOrder order = service.create(request("Broken door closer", priority, Category.GENERAL));

        assertThat(order.id()).isEqualTo("WO-1001");
        assertThat(order.status()).isEqualTo(Status.OPEN);
        assertThat(order.dueAt()).isEqualTo(START.plus(Duration.ofHours(hours)));
        assertThat(order.activity()).extracting(ActivityEntry::message)
                .containsExactly("Request created by Morgan Ellis");
    }

    @Test
    void createTrimsTextAndDropsBlankDescription() {
        WorkOrder order = service.create(new CreateWorkOrderRequest(
                "  Leaking pipe  ", "   ", " Tower B · Floor 2 ", Category.PLUMBING, Priority.HIGH, " Riley Patel "));

        assertThat(order.title()).isEqualTo("Leaking pipe");
        assertThat(order.description()).isNull();
        assertThat(order.location()).isEqualTo("Tower B · Floor 2");
        assertThat(order.requester()).isEqualTo("Riley Patel");
    }

    @Test
    void assigningAnOpenWorkOrderMovesItToAssigned() {
        WorkOrder order = service.create(request("No hot water", Priority.HIGH, Category.PLUMBING));

        service.assign(order.id(), "Priya Shah");

        assertThat(order.status()).isEqualTo(Status.ASSIGNED);
        assertThat(order.assignee()).isEqualTo("Priya Shah");
        assertThat(order.activity()).last().extracting(ActivityEntry::message).isEqualTo("Assigned to Priya Shah");
    }

    @Test
    void reassigningAnInProgressWorkOrderKeepsItsStatus() {
        WorkOrder order = service.create(request("No hot water", Priority.HIGH, Category.PLUMBING));
        service.assign(order.id(), "Priya Shah");
        service.changeStatus(order.id(), Status.IN_PROGRESS, null);

        service.assign(order.id(), "Jordan Lee");

        assertThat(order.status()).isEqualTo(Status.IN_PROGRESS);
        assertThat(order.activity()).last().extracting(ActivityEntry::message)
                .isEqualTo("Reassigned from Priya Shah to Jordan Lee");
    }

    @Test
    void closedWorkOrdersCannotBeReassigned() {
        WorkOrder order = service.create(request("Duplicate request", Priority.LOW, Category.GENERAL));
        service.changeStatus(order.id(), Status.CANCELLED, "Duplicate of WO-1000");

        assertThatThrownBy(() -> service.assign(order.id(), "Alex Kim"))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("cannot be reassigned");
    }

    @Test
    void completingAWorkOrderRecordsCompletionTimeAndNote() {
        WorkOrder order = service.create(request("Replace filter", Priority.MEDIUM, Category.HVAC));
        service.assign(order.id(), "Jordan Lee");
        service.changeStatus(order.id(), Status.IN_PROGRESS, null);
        clock.advance(Duration.ofHours(2));

        service.changeStatus(order.id(), Status.COMPLETED, "  Filter replaced  ");

        assertThat(order.status()).isEqualTo(Status.COMPLETED);
        assertThat(order.completedAt()).isEqualTo(START.plus(Duration.ofHours(2)));
        assertThat(order.activity()).last().extracting(ActivityEntry::message)
                .isEqualTo("Status changed from In progress to Completed: Filter replaced");
    }

    @Test
    void statusChangesMustFollowTheWorkflow() {
        WorkOrder order = service.create(request("Replace filter", Priority.MEDIUM, Category.HVAC));

        assertThatThrownBy(() -> service.changeStatus(order.id(), Status.COMPLETED, null))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessage("Cannot move WO-1001 from Open to Completed");
        assertThat(order.status()).isEqualTo(Status.OPEN);
    }

    @Test
    void assigningRequiresTheAssignAction() {
        WorkOrder order = service.create(request("Replace filter", Priority.MEDIUM, Category.HVAC));

        assertThatThrownBy(() -> service.changeStatus(order.id(), Status.ASSIGNED, null))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("assign action");
    }

    @Test
    void workOrderBecomesOverdueOnlyAfterItsResponseTarget() {
        WorkOrder order = service.create(request("Badge reader offline", Priority.URGENT, Category.SECURITY));

        clock.advance(Duration.ofHours(4));
        assertThat(service.isOverdue(order)).isFalse();

        clock.advance(Duration.ofMinutes(1));
        assertThat(service.isOverdue(order)).isTrue();
    }

    @Test
    void closedWorkOrdersAreNeverOverdue() {
        WorkOrder order = service.create(request("Badge reader offline", Priority.URGENT, Category.SECURITY));
        service.changeStatus(order.id(), Status.CANCELLED, null);

        clock.advance(Duration.ofDays(2));

        assertThat(service.isOverdue(order)).isFalse();
    }

    @Test
    void urgentWorkOrderIsAtRiskFromThreeQuartersOfItsWindowUntilDue() {
        WorkOrder order = service.create(request("Badge reader offline", Priority.URGENT, Category.SECURITY));

        clock.advance(Duration.ofHours(2).plusMinutes(59));
        assertThat(service.isAtRisk(order)).isFalse();
        assertThat(service.isOverdue(order)).isFalse();

        clock.advance(Duration.ofMinutes(1));
        assertThat(service.isAtRisk(order)).isTrue();
        assertThat(service.isOverdue(order)).isFalse();

        clock.advance(Duration.ofHours(1));
        assertThat(service.isAtRisk(order)).isTrue();
        assertThat(service.isOverdue(order)).isFalse();

        clock.advance(Duration.ofMinutes(1));
        assertThat(service.isAtRisk(order)).isFalse();
        assertThat(service.isOverdue(order)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"URGENT, 180", "HIGH, 1080", "MEDIUM, 3240", "LOW, 7560"})
    void atRiskThresholdIsThreeQuartersOfEveryPriorityWindow(Priority priority, long thresholdMinutes) {
        WorkOrder order = service.create(request("Broken door closer", priority, Category.GENERAL));

        clock.advance(Duration.ofMinutes(thresholdMinutes - 1));
        assertThat(service.isAtRisk(order)).isFalse();

        clock.advance(Duration.ofMinutes(1));
        assertThat(service.isAtRisk(order)).isTrue();
    }

    @Test
    void closedWorkOrdersAreNeverAtRisk() {
        WorkOrder completed = service.create(request("Replace filter", Priority.URGENT, Category.HVAC));
        service.assign(completed.id(), "Jordan Lee");
        service.changeStatus(completed.id(), Status.IN_PROGRESS, null);
        service.changeStatus(completed.id(), Status.COMPLETED, null);
        WorkOrder cancelled = service.create(request("Duplicate request", Priority.URGENT, Category.GENERAL));
        service.changeStatus(cancelled.id(), Status.CANCELLED, null);

        clock.advance(Duration.ofHours(3).plusMinutes(30));

        assertThat(service.isAtRisk(completed)).isFalse();
        assertThat(service.isAtRisk(cancelled)).isFalse();
    }

    @Test
    void assignedInProgressAndOnHoldOrdersCanBeAtRisk() {
        WorkOrder assigned = service.create(request("No hot water", Priority.URGENT, Category.PLUMBING));
        service.assign(assigned.id(), "Priya Shah");
        WorkOrder inProgress = service.create(request("Power outage", Priority.URGENT, Category.ELECTRICAL));
        service.assign(inProgress.id(), "Alex Kim");
        service.changeStatus(inProgress.id(), Status.IN_PROGRESS, null);
        WorkOrder onHold = service.create(request("HVAC not cooling", Priority.URGENT, Category.HVAC));
        service.assign(onHold.id(), "Jordan Lee");
        service.changeStatus(onHold.id(), Status.IN_PROGRESS, null);
        service.changeStatus(onHold.id(), Status.ON_HOLD, "Waiting for parts");

        clock.advance(Duration.ofHours(3));

        assertThat(List.of(assigned, inProgress, onHold)).extracting(WorkOrder::status)
                .containsExactly(Status.ASSIGNED, Status.IN_PROGRESS, Status.ON_HOLD);
        assertThat(List.of(assigned, inProgress, onHold)).allMatch(service::isAtRisk);
    }

    @Test
    void atRiskFilterCombinesWithOtherFilters() {
        AtRiskMix mix = seedAtRiskMix();

        assertThat(ids(new WorkOrderFilter(null, null, null, null, true, null, null, null)))
                .containsExactlyInAnyOrder(mix.urgentAtRisk().id(), mix.highAtRisk().id());
        assertThat(ids(new WorkOrderFilter(null, null, null, null, false, null, null, null)))
                .containsExactlyInAnyOrder(mix.urgentFresh().id(), mix.urgentOverdue().id(), mix.completed().id());
        assertThat(ids(new WorkOrderFilter(null, Priority.URGENT, null, null, true, null, null, null)))
                .containsExactly(mix.urgentAtRisk().id());
        assertThat(ids(new WorkOrderFilter(null, null, null, null, true, null, null, "tower")))
                .containsExactlyInAnyOrder(mix.urgentAtRisk().id(), mix.highAtRisk().id());
        assertThat(ids(new WorkOrderFilter(null, null, null, null, true, null, null, "hvac"))).isEmpty();
        assertThat(ids(new WorkOrderFilter(null, null, null, null, false, null, null, "sink"))).isEmpty();
        assertThat(ids(new WorkOrderFilter(null, null, null, null, false, null, null, "hvac")))
                .containsExactly(mix.urgentOverdue().id());
    }

    @Test
    void summaryCountsAtRiskWork() {
        seedAtRiskMix();

        WorkOrderSummary summary = service.summary();

        assertThat(summary.atRisk())
                .isEqualTo(ids(new WorkOrderFilter(null, null, null, null, true, null, null, null)).size())
                .isEqualTo(2);
        assertThat(summary.overdue()).isEqualTo(1);
    }
    @Test
    void filtersCombineStatusPriorityCategoryOverdueAndSearch() {
        WorkOrder hvac = service.create(request("HVAC not cooling", Priority.URGENT, Category.HVAC));
        service.create(request("Lights flickering", Priority.HIGH, Category.ELECTRICAL));
        WorkOrder leak = service.create(request("Sink leaking", Priority.URGENT, Category.PLUMBING));
        service.assign(leak.id(), "Priya Shah");
        clock.advance(Duration.ofHours(5));

        assertThat(ids(new WorkOrderFilter(null, Priority.URGENT, null, null, null, null, null, null)))
                .containsExactly(hvac.id(), leak.id());
        assertThat(ids(new WorkOrderFilter(Status.ASSIGNED, null, null, null, null, null, null, null))).containsExactly(leak.id());
        assertThat(ids(new WorkOrderFilter(null, null, Category.HVAC, true, null, null, null, null))).containsExactly(hvac.id());
        assertThat(ids(new WorkOrderFilter(null, null, null, null, null, null, null, "priya"))).containsExactly(leak.id());
        assertThat(ids(new WorkOrderFilter(null, null, null, null, null, null, null, "wo-1002"))).hasSize(1);
    }

    @Test
    void listShowsActiveWorkOrdersFirstByDueDate() {
        WorkOrder low = service.create(request("Paint touch-up", Priority.LOW, Category.GENERAL));
        WorkOrder urgent = service.create(request("Power outage", Priority.URGENT, Category.ELECTRICAL));
        WorkOrder done = service.create(request("Swap light bulb", Priority.URGENT, Category.ELECTRICAL));
        service.assign(done.id(), "Alex Kim");
        service.changeStatus(done.id(), Status.IN_PROGRESS, null);
        service.changeStatus(done.id(), Status.COMPLETED, null);

        assertThat(ids(WorkOrderFilter.none())).containsExactly(urgent.id(), low.id(), done.id());
    }

    @Test
    void summaryCountsActiveOverdueAndUrgentWork() {
        service.create(request("Power outage", Priority.URGENT, Category.ELECTRICAL));
        service.create(request("Paint touch-up", Priority.LOW, Category.GENERAL));
        WorkOrder cancelled = service.create(request("Duplicate", Priority.URGENT, Category.GENERAL));
        service.changeStatus(cancelled.id(), Status.CANCELLED, null);
        clock.advance(Duration.ofHours(5));

        WorkOrderSummary summary = service.summary();

        assertThat(summary.total()).isEqualTo(3);
        assertThat(summary.active()).isEqualTo(2);
        assertThat(summary.overdue()).isEqualTo(1);
        assertThat(summary.urgentActive()).isEqualTo(1);
        assertThat(summary.byStatus()).containsEntry(Status.OPEN, 2L).containsEntry(Status.CANCELLED, 1L)
                .containsEntry(Status.COMPLETED, 0L);
    }

    @Test
    void workloadCountsOnlyActiveOrdersPerTechnicianAndUnassigned() {
        WorkOrder assigned = service.create(request("No hot water", Priority.URGENT, Category.PLUMBING));
        service.assign(assigned.id(), "Priya Shah");
        WorkOrder inProgress = service.create(request("Power outage", Priority.LOW, Category.ELECTRICAL));
        service.assign(inProgress.id(), "Priya Shah");
        service.changeStatus(inProgress.id(), Status.IN_PROGRESS, null);
        WorkOrder onHold = service.create(request("HVAC not cooling", Priority.LOW, Category.HVAC));
        service.assign(onHold.id(), "Priya Shah");
        service.changeStatus(onHold.id(), Status.IN_PROGRESS, null);
        service.changeStatus(onHold.id(), Status.ON_HOLD, null);
        WorkOrder completed = service.create(request("Replace filter", Priority.URGENT, Category.HVAC));
        service.assign(completed.id(), "Priya Shah");
        service.changeStatus(completed.id(), Status.IN_PROGRESS, null);
        service.changeStatus(completed.id(), Status.COMPLETED, null);
        WorkOrder cancelled = service.create(request("Duplicate", Priority.URGENT, Category.GENERAL));
        service.assign(cancelled.id(), "Alex Kim");
        service.changeStatus(cancelled.id(), Status.CANCELLED, null);
        service.create(request("Lights flickering", Priority.URGENT, Category.ELECTRICAL));
        service.create(request("Paint touch-up", Priority.LOW, Category.GENERAL));
        WorkOrder cancelledOpen = service.create(request("Wrong building", Priority.URGENT, Category.GENERAL));
        service.changeStatus(cancelledOpen.id(), Status.CANCELLED, null);
        clock.advance(Duration.ofHours(5));

        WorkOrderWorkload workload = service.workload();

        assertThat(workload.technicians()).containsExactly(new TechnicianWorkload("Priya Shah", 3, 1));
        assertThat(workload.unassigned()).isEqualTo(new WorkloadCount(2, 1));

        service.create(request("Badge reader offline", Priority.URGENT, Category.SECURITY));
        assertThat(service.workload().unassigned()).isEqualTo(new WorkloadCount(3, 1));
    }

    @Test
    void workloadSortsByActiveThenNameCaseInsensitively() {
        WorkOrder sam = service.create(request("Deep clean", Priority.MEDIUM, Category.CLEANING));
        service.assign(sam.id(), "sam Rivera");
        WorkOrder priya = service.create(request("Sink leaking", Priority.HIGH, Category.PLUMBING));
        service.assign(priya.id(), "Priya Shah");
        WorkOrder jordan = service.create(request("HVAC not cooling", Priority.URGENT, Category.HVAC));
        service.assign(jordan.id(), "Jordan Lee");

        assertThat(service.workload().technicians()).extracting(TechnicianWorkload::assignee)
                .containsExactly("Jordan Lee", "Priya Shah", "sam Rivera");

        service.assign(jordan.id(), "Priya Shah");

        assertThat(service.workload().technicians()).containsExactly(
                new TechnicianWorkload("Priya Shah", 2, 0),
                new TechnicianWorkload("sam Rivera", 1, 0));
    }

    @Test
    void workloadGroupsNameCasingVariants() {
        WorkOrder first = service.create(request("HVAC not cooling", Priority.URGENT, Category.HVAC));
        service.assign(first.id(), "Jordan Lee");
        clock.advance(Duration.ofMinutes(5));
        WorkOrder second = service.create(request("Replace filter", Priority.LOW, Category.HVAC));
        service.assign(second.id(), " jordan lee ");

        assertThat(service.workload().technicians()).containsExactly(new TechnicianWorkload("Jordan Lee", 2, 0));
    }

    @Test
    void workloadDropsTechnicianWhoseLastActiveOrderCloses() {
        WorkOrder completing = service.create(request("Replace filter", Priority.MEDIUM, Category.HVAC));
        service.assign(completing.id(), "Jordan Lee");
        service.changeStatus(completing.id(), Status.IN_PROGRESS, null);
        WorkOrder cancelling = service.create(request("Sink leaking", Priority.HIGH, Category.PLUMBING));
        service.assign(cancelling.id(), "Priya Shah");

        service.changeStatus(completing.id(), Status.ON_HOLD, null);
        assertThat(service.workload().technicians()).containsExactly(
                new TechnicianWorkload("Jordan Lee", 1, 0),
                new TechnicianWorkload("Priya Shah", 1, 0));
        service.changeStatus(completing.id(), Status.IN_PROGRESS, null);
        assertThat(service.workload().technicians()).containsExactly(
                new TechnicianWorkload("Jordan Lee", 1, 0),
                new TechnicianWorkload("Priya Shah", 1, 0));

        service.changeStatus(completing.id(), Status.COMPLETED, null);
        service.changeStatus(cancelling.id(), Status.CANCELLED, null);

        assertThat(service.workload().technicians()).isEmpty();
        assertThat(service.workload().unassigned()).isEqualTo(new WorkloadCount(0, 0));
    }

    @Test
    void assigneeFilterIsExactAndCaseInsensitive() {
        WorkOrder hvac = service.create(request("HVAC not cooling", Priority.URGENT, Category.HVAC));
        service.assign(hvac.id(), "Jordan Lee");
        WorkOrder done = service.create(request("Replace filter", Priority.LOW, Category.HVAC));
        service.assign(done.id(), "Jordan Lee");
        service.changeStatus(done.id(), Status.IN_PROGRESS, null);
        service.changeStatus(done.id(), Status.COMPLETED, null);
        service.create(request("Lights flickering", Priority.HIGH, Category.ELECTRICAL));

        assertThat(ids(new WorkOrderFilter(null, null, null, null, null, " jordan lee ", null, null)))
                .containsExactly(hvac.id(), done.id());
        assertThat(ids(new WorkOrderFilter(null, null, null, null, null, "Jordan", null, null))).isEmpty();
        assertThat(ids(new WorkOrderFilter(Status.COMPLETED, null, null, null, null, "Jordan Lee", null, null)))
                .containsExactly(done.id());
        assertThat(ids(new WorkOrderFilter(null, null, null, null, null, "  ", null, null))).hasSize(3);
    }

    @Test
    void unassignedFilterReturnsOnlyActiveOrdersWithoutTechnician() {
        WorkOrder open = service.create(request("Lights flickering", Priority.HIGH, Category.ELECTRICAL));
        WorkOrder assigned = service.create(request("HVAC not cooling", Priority.URGENT, Category.HVAC));
        service.assign(assigned.id(), "Jordan Lee");
        WorkOrder cancelled = service.create(request("Duplicate", Priority.LOW, Category.GENERAL));
        service.changeStatus(cancelled.id(), Status.CANCELLED, null);

        assertThat(ids(new WorkOrderFilter(null, null, null, null, null, null, true, null)))
                .containsExactly(open.id());
        assertThat(ids(new WorkOrderFilter(null, null, null, null, null, null, false, null))).hasSize(3);
    }

    @Test
    void unknownWorkOrderIsNotFound() {
        assertThatThrownBy(() -> service.get("WO-9999"))
                .isInstanceOf(WorkOrderNotFoundException.class)
                .hasMessage("No work order exists with ID WO-9999");
    }

    private AtRiskMix seedAtRiskMix() {
        WorkOrder urgentAtRisk = service.createAt(
                request("Sink leaking", Priority.URGENT, Category.PLUMBING), START.minus(Duration.ofMinutes(210)));
        WorkOrder highAtRisk = service.createAt(
                request("Lights flickering", Priority.HIGH, Category.ELECTRICAL), START.minus(Duration.ofHours(20)));
        WorkOrder urgentFresh = service.create(request("Power outage", Priority.URGENT, Category.ELECTRICAL));
        WorkOrder urgentOverdue = service.createAt(
                request("HVAC not cooling", Priority.URGENT, Category.HVAC), START.minus(Duration.ofHours(5)));
        WorkOrder completed = service.createAt(
                request("Replace filter", Priority.URGENT, Category.HVAC), START.minus(Duration.ofMinutes(210)));
        service.assign(completed.id(), "Jordan Lee");
        service.changeStatus(completed.id(), Status.IN_PROGRESS, null);
        service.changeStatus(completed.id(), Status.COMPLETED, null);
        return new AtRiskMix(urgentAtRisk, highAtRisk, urgentFresh, urgentOverdue, completed);
    }

    private record AtRiskMix(WorkOrder urgentAtRisk, WorkOrder highAtRisk, WorkOrder urgentFresh,
                             WorkOrder urgentOverdue, WorkOrder completed) {
    }

    private List<String> ids(WorkOrderFilter filter) {        return service.list(filter).stream().map(WorkOrder::id).toList();
    }

    private static CreateWorkOrderRequest request(String title, Priority priority, Category category) {
        return new CreateWorkOrderRequest(title, "Details", "Tower A · Floor 2", category, priority, "Morgan Ellis");
    }
}
