package com.example.facilities.workorder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DemoDataLoaderTest {

    @Autowired
    private WorkOrderService service;

    @Test
    void sampleDataCoversActiveOverdueAndCompletedWork() {
        WorkOrderSummary summary = service.summary();

        assertThat(summary.total()).isEqualTo(7);
        assertThat(summary.overdue()).isEqualTo(2);
        assertThat(summary.atRisk()).isEqualTo(1);
        assertThat(summary.byStatus())
                .containsEntry(Status.OPEN, 3L)
                .containsEntry(Status.ASSIGNED, 2L)
                .containsEntry(Status.IN_PROGRESS, 1L)
                .containsEntry(Status.COMPLETED, 1L);
    }
}
