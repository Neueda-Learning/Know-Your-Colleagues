package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.AlertStatus;
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
