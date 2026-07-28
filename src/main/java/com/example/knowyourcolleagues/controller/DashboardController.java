package com.example.knowyourcolleagues.controller;

import com.example.knowyourcolleagues.dto.dashboard.DashboardSnapshot;
import com.example.knowyourcolleagues.enums.DashboardUpdateType;
import com.example.knowyourcolleagues.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Transaction-risk dashboard data")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(
            summary = "Get the complete dashboard snapshot",
            description = "Returns the same complete data snapshot sent when a dashboard WebSocket connects."
    )
    public ResponseEntity<DashboardSnapshot> getDashboard() {
        return ResponseEntity.ok(
                dashboardService.getSnapshot(DashboardUpdateType.FULL)
        );
    }
}
