package com.example.facilities.workorder;

import java.util.List;

public record WorkOrderWorkload(List<TechnicianWorkload> technicians, WorkloadCount unassigned) {
}
