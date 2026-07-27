package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.AlertStatus;
import com.example.knowyourcolleagues.enums.Severity;
import lombok.Data;

import java.time.Instant;
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
    private Instant createdAt;
    private Instant acknowledgedAt;
    private Instant investigatingAt;
    private Instant closedAt;
    private Instant dismissedAt;
    private Instant updatedAt;
    private Integer version;
    private List<Long> relatedTransactionIds;
    private List<AlertHistoryResponse> history;
}
