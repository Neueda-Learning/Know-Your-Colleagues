package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.AlertStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class AlertHistoryResponse {

    private Long id;
    private AlertStatus fromStatus;
    private AlertStatus toStatus;
    private String notes;
    private Instant changedAt;
}
