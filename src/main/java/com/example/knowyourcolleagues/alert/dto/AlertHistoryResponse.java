package com.example.knowyourcolleagues.alert.dto;

import com.example.knowyourcolleagues.alert.enums.AlertStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlertHistoryResponse {

    private Long id;
    private AlertStatus fromStatus;
    private AlertStatus toStatus;
    private String notes;
    private LocalDateTime changedAt;
}
