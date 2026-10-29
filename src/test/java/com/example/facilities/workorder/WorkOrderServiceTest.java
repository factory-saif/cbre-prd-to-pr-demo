package com.example.facilities.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.facilities.workorder.api.CreateWorkOrderRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
    void changeStatusAtRecordsCompletionAndActivityAtTheGivenTime() {
        WorkOrder order = service.create(request("Clear slow drain", Priority.MEDIUM, Category.PLUMBING));
        Instant startedAt = START.plus(Duration.ofHours(1));
        Instant completedAt = START.plus(Duration.ofHours(5));
        service.assignAt(order.id(), "Priya Shah", startedAt);
        service.changeStatusAt(order.id(), Status.IN_PROGRESS, null, startedAt);

        service.changeStatusAt(order.id(), Status.COMPLETED, "Drain cleared", completedAt);

        assertThat(order.status()).isEqualTo(Status.COMPLETED);
        assertThat(order.completedAt()).isEqualTo(completedAt);
        assertThat(order.activity()).last().satisfies(entry -> {
            assertThat(entry.at()).isEqualTo(completedAt);
            assertThat(entry.message()).isEqualTo("Status changed from In progress to Completed: Drain cleared");
        });
    }

    @Test
    void assignAndChangeStatusReadTheClockWhileHoldingTheServiceLock() {
        List<Boolean> lockHeldAtClockRead = new ArrayList<>();
        WorkOrderService[] probed = new WorkOrderService[1];
        Clock probe = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                lockHeldAtClockRead.add(Thread.holdsLock(probed[0]));
                return START;
            }
        };
        probed[0] = new WorkOrderService(new WorkOrderRepository(), probe);
        WorkOrder order = probed[0].create(request("Replace filter", Priority.MEDIUM, Category.HVAC));
        lockHeldAtClockRead.clear();

        probed[0].assign(order.id(), "Priya Shah");
        probed[0].changeStatus(order.id(), Status.IN_PROGRESS, null);

        assertThat(lockHeldAtClockRead).isNotEmpty().containsOnly(true);
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

        assertThat(ids(new WorkOrderFilter(null, null, null, null, true, null)))
                .containsExactlyInAnyOrder(mix.urgentAtRisk().id(), mix.highAtRisk().id());
        assertThat(ids(new WorkOrderFilter(null, null, null, null, false, null)))
                .containsExactlyInAnyOrder(mix.urgentFresh().id(), mix.urgentOverdue().id(), mix.completed().id());
        assertThat(ids(new WorkOrderFilter(null, Priority.URGENT, null, null, true, null)))
                .containsExactly(mix.urgentAtRisk().id());
        assertThat(ids(new WorkOrderFilter(null, null, null, null, true, "tower")))
                .containsExactlyInAnyOrder(mix.urgentAtRisk().id(), mix.highAtRisk().id());
        assertThat(ids(new WorkOrderFilter(null, null, null, null, true, "hvac"))).isEmpty();
        assertThat(ids(new WorkOrderFilter(null, null, null, null, false, "sink"))).isEmpty();
        assertThat(ids(new WorkOrderFilter(null, null, null, null, false, "hvac")))
                .containsExactly(mix.urgentOverdue().id());
    }

    @Test
    void summaryCountsAtRiskWork() {
        seedAtRiskMix();

        WorkOrderSummary summary = service.summary();

        assertThat(summary.atRisk())
                .isEqualTo(ids(new WorkOrderFilter(null, null, null, null, true, null)).size())
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

        assertThat(ids(new WorkOrderFilter(null, Priority.URGENT, null, null, null, null)))
                .containsExactly(hvac.id(), leak.id());
        assertThat(ids(new WorkOrderFilter(Status.ASSIGNED, null, null, null, null, null))).containsExactly(leak.id());
        assertThat(ids(new WorkOrderFilter(null, null, Category.HVAC, true, null, null))).containsExactly(hvac.id());
        assertThat(ids(new WorkOrderFilter(null, null, null, null, null, "priya"))).containsExactly(leak.id());
        assertThat(ids(new WorkOrderFilter(null, null, null, null, null, "wo-1002"))).hasSize(1);
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
    void unknownWorkOrderIsNotFound() {
        assertThatThrownBy(() -> service.get("WO-9999"))
                .isInstanceOf(WorkOrderNotFoundException.class)
                .hasMessage("No work order exists with ID WO-9999");
    }

    @Test
    void reopenClearsCompletionAndRestartsTheResponseTargetFromTheClock() {
        WorkOrder order = completedOrder(Priority.HIGH);
        clock.advance(Duration.ofHours(23));

        service.reopen(order.id(), "  Leak is dripping again  ");

        Instant reopenedAt = START.plus(Duration.ofHours(24));
        assertThat(order.status()).isEqualTo(Status.IN_PROGRESS);
        assertThat(order.assignee()).isEqualTo("Jordan Lee");
        assertThat(order.completedAt()).isNull();
        assertThat(order.reopenedAt()).isEqualTo(reopenedAt);
        assertThat(order.dueAt()).isEqualTo(reopenedAt.plus(Duration.ofHours(24)));
        assertThat(order.reopenCount()).isEqualTo(1);
        assertThat(order.isReopened()).isTrue();
        assertThat(order.activity()).last().extracting(ActivityEntry::message)
                .isEqualTo("Status changed from Completed to In progress: Reopened: Leak is dripping again");
    }

    @Test
    void reopenedOrderIsAtRiskFromItsNewWindow() {
        WorkOrder order = completedOrder(Priority.URGENT);
        clock.advance(Duration.ofHours(3));
        service.reopen(order.id(), "Still broken");

        assertThat(service.isAtRisk(order)).isFalse();

        clock.advance(Duration.ofHours(2).plusMinutes(59));
        assertThat(service.isAtRisk(order)).isFalse();

        clock.advance(Duration.ofMinutes(1));
        assertThat(service.isAtRisk(order)).isTrue();
        assertThat(service.isOverdue(order)).isFalse();
    }

    @Test
    void reopenIsRejectedOutsideTheWindowAndForOrdersThatAreNotCompleted() {
        WorkOrder completed = completedOrder(Priority.HIGH);
        WorkOrder open = service.create(request("Paint touch-up", Priority.LOW, Category.GENERAL));
        clock.advance(Duration.ofDays(7).plusSeconds(1));

        assertThatThrownBy(() -> service.reopen(completed.id(), "Still broken"))
                .isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> service.reopen(open.id(), "Still broken"))
                .isInstanceOf(InvalidTransitionException.class);
        assertThat(completed.status()).isEqualTo(Status.COMPLETED);
        assertThat(completed.reopenCount()).isZero();
    }

    @Test
    void reopeningAgainRecordsTheLatestReopenTime() {
        WorkOrder order = completedOrder(Priority.HIGH);
        clock.advance(Duration.ofDays(1));
        service.reopen(order.id(), "Leak is dripping again");
        clock.advance(Duration.ofDays(4));
        service.changeStatus(order.id(), Status.COMPLETED, null);
        clock.advance(Duration.ofDays(3));

        service.reopen(order.id(), "Leaking for a third time");

        assertThat(order.reopenCount()).isEqualTo(2);
        assertThat(order.reopenedAt()).isEqualTo(START.plus(Duration.ofHours(1)).plus(Duration.ofDays(8)));
    }

    @Test
    void changingPriorityRecalculatesDueTimeFromTheWindowStart() {
        WorkOrder order = service.create(request("Leak near server racks", Priority.MEDIUM, Category.PLUMBING));
        clock.advance(Duration.ofHours(2));

        service.changePriority(order.id(), Priority.URGENT, "water near the server racks");

        assertThat(order.priority()).isEqualTo(Priority.URGENT);
        assertThat(order.dueAt()).isEqualTo(START.plus(Duration.ofHours(4)));
        assertThat(service.isOverdue(order)).isFalse();
        assertThat(service.isAtRisk(order)).isFalse();

        clock.advance(Duration.ofHours(1));
        assertThat(service.isAtRisk(order)).isTrue();
        assertThat(service.isOverdue(order)).isFalse();
    }

    @Test
    void raisingPriorityPastTheNewTargetMakesTheOrderOverdueImmediately() {
        WorkOrder order = service.create(request("Leak near server racks", Priority.MEDIUM, Category.PLUMBING));
        clock.advance(Duration.ofHours(5));

        service.changePriority(order.id(), Priority.URGENT, "water near the server racks");

        assertThat(order.dueAt()).isEqualTo(START.plus(Duration.ofHours(4)));
        assertThat(service.isOverdue(order)).isTrue();
        assertThat(service.isAtRisk(order)).isFalse();
    }

    @Test
    void loweringPriorityCanClearOverdue() {
        WorkOrder order = service.create(request("Scuff on lobby wall", Priority.URGENT, Category.GENERAL));
        clock.advance(Duration.ofHours(5));
        assertThat(service.isOverdue(order)).isTrue();

        service.changePriority(order.id(), Priority.LOW, "cosmetic only");

        assertThat(order.dueAt()).isEqualTo(START.plus(Duration.ofDays(7)));
        assertThat(service.isOverdue(order)).isFalse();
        assertThat(service.isAtRisk(order)).isFalse();
    }

    @Test
    void changingPriorityOfAReopenedOrderUsesTheLatestReopenTime() {
        WorkOrder order = completedOrder(Priority.HIGH);
        clock.advance(Duration.ofHours(2));
        service.reopen(order.id(), "Leak is dripping again");
        clock.advance(Duration.ofHours(1));

        service.changePriority(order.id(), Priority.URGENT, "water near the server racks");

        assertThat(order.reopenedAt()).isEqualTo(START.plus(Duration.ofHours(3)));
        assertThat(order.dueAt()).isEqualTo(order.reopenedAt().plus(Duration.ofHours(4)));
        assertThat(order.createdAt()).isEqualTo(START);
    }

    @Test
    void changingPriorityAddsAnActivityEntryAndKeepsStatusAndAssignee() {
        WorkOrder order = service.create(request("Leak near server racks", Priority.MEDIUM, Category.PLUMBING));
        service.assign(order.id(), "Priya Shah");
        List<ActivityEntry> before = order.activity();
        clock.advance(Duration.ofHours(2));

        service.changePriority(order.id(), Priority.URGENT, "  water near the server racks  ");

        assertThat(order.status()).isEqualTo(Status.ASSIGNED);
        assertThat(order.assignee()).isEqualTo("Priya Shah");
        assertThat(order.updatedAt()).isEqualTo(START.plus(Duration.ofHours(2)));
        assertThat(order.activity()).hasSize(before.size() + 1).startsWith(before.toArray(ActivityEntry[]::new));
        assertThat(order.activity()).last().satisfies(entry -> {
            assertThat(entry.at()).isEqualTo(START.plus(Duration.ofHours(2)));
            assertThat(entry.message()).isEqualTo("Priority changed from Medium to Urgent: water near the server racks");
        });
    }

    @Test
    void changingPriorityIsRejectedForClosedOrdersAndTheSamePriority() {
        WorkOrder completed = completedOrder(Priority.MEDIUM);
        WorkOrder cancelled = service.create(request("Duplicate request", Priority.MEDIUM, Category.GENERAL));
        service.changeStatus(cancelled.id(), Status.CANCELLED, null);
        WorkOrder active = service.create(request("Leak near server racks", Priority.MEDIUM, Category.PLUMBING));

        for (WorkOrder order : List.of(completed, cancelled, active)) {
            Instant dueAt = order.dueAt();
            int activitySize = order.activity().size();
            Priority next = order == active ? Priority.MEDIUM : Priority.URGENT;

            assertThatThrownBy(() -> service.changePriority(order.id(), next, "water near the server racks"))
                    .isInstanceOf(InvalidTransitionException.class);
            assertThat(order.priority()).isEqualTo(Priority.MEDIUM);
            assertThat(order.dueAt()).isEqualTo(dueAt);
            assertThat(order.activity()).hasSize(activitySize);
        }
    }

    private WorkOrder completedOrder(Priority priority) {
        WorkOrder order = service.create(request("Sink leaking", priority, Category.PLUMBING));
        service.assign(order.id(), "Jordan Lee");
        service.changeStatus(order.id(), Status.IN_PROGRESS, null);
        clock.advance(Duration.ofHours(1));
        service.changeStatus(order.id(), Status.COMPLETED, null);
        return order;
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
