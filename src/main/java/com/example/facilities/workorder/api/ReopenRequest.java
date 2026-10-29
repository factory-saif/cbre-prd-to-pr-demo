package com.example.facilities.workorder.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReopenRequest(@NotBlank @Size(max = 500) String reason) {
}
