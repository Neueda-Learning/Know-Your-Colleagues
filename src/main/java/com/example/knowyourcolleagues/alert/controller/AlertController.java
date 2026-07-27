package com.example.knowyourcolleagues.alert.controller;

import com.example.knowyourcolleagues.alert.dto.AlertDetailResponse;
import com.example.knowyourcolleagues.alert.dto.AlertHistoryResponse;
import com.example.knowyourcolleagues.alert.dto.AlertPageResponse;
import com.example.knowyourcolleagues.alert.dto.UpdateAlertStatusRequest;
import com.example.knowyourcolleagues.alert.enums.AlertStatus;
import com.example.knowyourcolleagues.alert.enums.Severity;
import com.example.knowyourcolleagues.alert.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<AlertPageResponse> getAlerts(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) String accountId,
            @RequestParam(defaultValue = "0") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ResponseEntity.ok(
                alertService.getAlerts(status, severity, accountId, page, size)
        );
    }

    @GetMapping("/{alertId}")
    public ResponseEntity<AlertDetailResponse> getAlert(
            @PathVariable Long alertId
    ) {
        return ResponseEntity.ok(alertService.getAlert(alertId));
    }

    @PatchMapping("/{alertId}/status")
    public ResponseEntity<AlertDetailResponse> updateStatus(
            @PathVariable Long alertId,
            @RequestBody UpdateAlertStatusRequest request
    ) {
        return ResponseEntity.ok(alertService.updateStatus(alertId, request));
    }

    @GetMapping("/{alertId}/history")
    public ResponseEntity<List<AlertHistoryResponse>> getHistory(
            @PathVariable Long alertId
    ) {
        return ResponseEntity.ok(alertService.getHistory(alertId));
    }
}
