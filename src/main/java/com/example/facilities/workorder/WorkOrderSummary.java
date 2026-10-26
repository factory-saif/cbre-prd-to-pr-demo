package com.example.facilities.workorder;

import java.util.Map;

public record WorkOrderSummary(int total, long active, long overdue, long atRisk, long urgentActive,
                               Map<Status, Long> byStatus) {
}
