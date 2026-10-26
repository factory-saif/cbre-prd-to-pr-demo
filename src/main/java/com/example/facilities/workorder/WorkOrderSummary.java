package com.example.facilities.workorder;

import java.util.Map;

public record WorkOrderSummary(int total, long active, long overdue, long urgentActive, Map<Status, Long> byStatus) {
}
