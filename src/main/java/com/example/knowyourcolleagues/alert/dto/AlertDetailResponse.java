package com.example.knowyourcolleagues.alert.dto;

import com.example.knowyourcolleagues.alert.enums.AlertStatus;
import com.example.knowyourcolleagues.alert.enums.Severity;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AlertDetailResponse {

    private Long id;
    private Long ruleId;
    private Long triggerTransactionId;
    private String accountId;
    private String ruleName;
    private Severity severity;
    private AlertStatus status;
    private String title;
    private String description;
    private String resolutionNotes;
    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime investigatingAt;
    private LocalDateTime closedAt;
    private LocalDateTime dismissedAt;
    private LocalDateTime updatedAt;
    private Integer version;
    private List<AlertHistoryResponse> history;
}
