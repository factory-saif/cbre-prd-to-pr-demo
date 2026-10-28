package com.example.facilities.workorder.api;

import com.example.facilities.workorder.WorkOrder;
import java.util.List;

/**
 * Writes work orders as RFC 4180 CSV: a fixed header row, CRLF record endings, and fields quoted only when they
 * contain a comma, a double quote, or a line break.
 */
final class WorkOrderCsv {

    static final String HEADER = "id,title,category,priority,status,assignee,created at,due at";

    private static final String CRLF = "\r\n";

    private WorkOrderCsv() {
    }

    static String write(List<WorkOrder> orders) {
        StringBuilder csv = new StringBuilder(HEADER).append(CRLF);
        for (WorkOrder order : orders) {
            csv.append(String.join(",",
                            field(order.id()),
                            field(order.title()),
                            order.category().name(),
                            order.priority().name(),
                            order.status().name(),
                            field(order.assignee()),
                            order.createdAt().toString(),
                            order.dueAt().toString()))
                    .append(CRLF);
        }
        return csv.toString();
    }

    static String field(String value) {
        if (value == null) {
            return "";
        }
        if (!needsQuotes(value)) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static boolean needsQuotes(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ',' || c == '"' || c == '\n' || c == '\r') {
                return true;
            }
        }
        return false;
    }
}
