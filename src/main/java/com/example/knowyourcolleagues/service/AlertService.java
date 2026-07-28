package com.example.knowyourcolleagues.service;

import com.example.knowyourcolleagues.dto.AlertDetailResponse;
import com.example.knowyourcolleagues.dto.AlertHistoryResponse;
import com.example.knowyourcolleagues.dto.AlertPageResponse;
import com.example.knowyourcolleagues.dto.AlertResponse;
import com.example.knowyourcolleagues.dto.CreateAlertCommand;
import com.example.knowyourcolleagues.dto.UpdateAlertStatusRequest;
import com.example.knowyourcolleagues.enums.AlertStatus;
import com.example.knowyourcolleagues.enums.Severity;

import java.time.Instant;
import java.util.List;

public interface AlertService {

    AlertResponse createAlert(CreateAlertCommand command);

    AlertDetailResponse getAlert(Long alertId);

    AlertPageResponse getAlerts(
            AlertStatus status,
            Severity severity,
            String accountId,
            long page,
            long size,
            Instant createdAtStart
    );

    AlertDetailResponse updateStatus(Long alertId, UpdateAlertStatusRequest request);

    List<AlertHistoryResponse> getHistory(Long alertId);
}
