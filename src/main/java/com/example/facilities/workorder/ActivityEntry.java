package com.example.facilities.workorder;

import java.time.Instant;

public record ActivityEntry(Instant at, String message) {
}
