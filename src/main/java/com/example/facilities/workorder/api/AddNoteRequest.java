package com.example.facilities.workorder.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddNoteRequest(@NotBlank @Size(max = 500) String text, @NotBlank @Size(max = 100) String author) {
}
