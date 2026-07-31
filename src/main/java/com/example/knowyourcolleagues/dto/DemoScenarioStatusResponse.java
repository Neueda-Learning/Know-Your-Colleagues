package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.DemoScenarioState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Current live-demo scenario status")
public record DemoScenarioStatusResponse(
        DemoScenarioState state,
        String message,
        Integer transactionCount,
        Long intervalMs,
        Instant startedAt,
        Instant finishedAt,
        Integer exitCode
) {
}
