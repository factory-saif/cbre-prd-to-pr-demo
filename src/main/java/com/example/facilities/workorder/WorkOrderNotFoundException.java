package com.example.facilities.workorder;

public class WorkOrderNotFoundException extends RuntimeException {

    public WorkOrderNotFoundException(String id) {
        super("No work order exists with ID " + id);
    }
}
