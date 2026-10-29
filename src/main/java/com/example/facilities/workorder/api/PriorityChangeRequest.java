package com.example.facilities.workorder.api;

import com.example.facilities.workorder.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PriorityChangeRequest(@NotNull Priority priority, @NotBlank @Size(max = 500) String reason) {
}
