package com.example.knowyourcolleagues.alert.dto;

import com.example.knowyourcolleagues.alert.enums.AlertStatus;
import com.example.knowyourcolleagues.alert.enums.Severity;
import lombok.Data;

import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
