package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.AlertStatus;
import lombok.Data;

@Data
public class UpdateAlertStatusRequest {

    private AlertStatus targetStatus;
    private String notes;
}
