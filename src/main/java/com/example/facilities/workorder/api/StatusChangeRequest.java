package com.example.facilities.workorder.api;

import com.example.facilities.workorder.Status;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusChangeRequest(@NotNull Status status, @Size(max = 500) String note) {
}
