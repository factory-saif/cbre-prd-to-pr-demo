package com.example.facilities.workorder.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignRequest(@NotBlank @Size(max = 80) String assignee) {
}
