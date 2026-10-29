package com.example.facilities.workorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DemoDataLoaderTest {

    @Autowired
    private WorkOrderService service;

    @Autowired
    private Clock clock;

    @Test
    void sampleDataCoversActiveOverdueAndCompletedWork() {
        WorkOrderSummary summary = service.summary();

        assertThat(summary.total()).isEqualTo(8);
        assertThat(summary.overdue()).isEqualTo(2);
        assertThat(summary.atRisk()).isEqualTo(1);
        assertThat(summary.byStatus())
                .containsEntry(Status.OPEN, 3L)
                .containsEntry(Status.ASSIGNED, 2L)
                .containsEntry(Status.IN_PROGRESS, 1L)
                .containsEntry(Status.COMPLETED, 2L);
    }

    @Test
    void sampleDataHasCompletedOrdersInsideAndOutsideTheReopenWindow() {
        Instant reopenCutoff = clock.instant().minus(WorkOrderService.REOPEN_WINDOW);
        List<WorkOrder> completed = service.list(new WorkOrderFilter(Status.COMPLETED, null, null, null, null, null));

        assertThat(completed).hasSize(2);
        assertThat(completed).filteredOn(order -> !order.completedAt().isBefore(reopenCutoff)).hasSize(1);
        assertThat(completed).filteredOn(order -> order.completedAt().isBefore(reopenCutoff)).hasSize(1);
    }
}
