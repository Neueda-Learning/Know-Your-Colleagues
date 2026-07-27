package com.example.knowyourcolleagues.alert.service;

import com.example.knowyourcolleagues.alert.dto.AlertDetailResponse;
import com.example.knowyourcolleagues.alert.dto.AlertHistoryResponse;
import com.example.knowyourcolleagues.alert.dto.AlertPageResponse;
import com.example.knowyourcolleagues.alert.dto.AlertResponse;
import com.example.knowyourcolleagues.alert.dto.CreateAlertCommand;
import com.example.knowyourcolleagues.alert.dto.UpdateAlertStatusRequest;
import com.example.knowyourcolleagues.alert.enums.AlertStatus;
import com.example.knowyourcolleagues.alert.enums.Severity;

import java.util.List;

public interface AlertService {

    AlertResponse createAlert(CreateAlertCommand command);

    AlertDetailResponse getAlert(Long alertId);

    AlertPageResponse getAlerts(
            AlertStatus status,
            Severity severity,
            String accountId,
            long page,
            long size
    );

    AlertDetailResponse updateStatus(Long alertId, UpdateAlertStatusRequest request);

    List<AlertHistoryResponse> getHistory(Long alertId);
}
