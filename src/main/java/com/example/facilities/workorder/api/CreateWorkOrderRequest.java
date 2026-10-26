package com.example.facilities.workorder.api;

import com.example.facilities.workorder.Category;
import com.example.facilities.workorder.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWorkOrderRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 120) String location,
        @NotNull Category category,
        @NotNull Priority priority,
        @NotBlank @Size(max = 80) String requester) {
}
