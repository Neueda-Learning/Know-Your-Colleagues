package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.AlertStatus;
import com.example.knowyourcolleagues.enums.Severity;
import lombok.Data;

import java.time.Instant;

@Data
public class AlertResponse {

    private Long id;
    private Long ruleId;
    private Long triggerTransactionId;
    private String accountId;
    private String ruleName;
    private Severity severity;
    private AlertStatus status;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;
}
