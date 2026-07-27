package com.example.knowyourcolleagues.alert.dto;

import com.example.knowyourcolleagues.alert.enums.AlertStatus;
import lombok.Data;

@Data
public class UpdateAlertStatusRequest {

    private AlertStatus targetStatus;
    private String notes;
}
