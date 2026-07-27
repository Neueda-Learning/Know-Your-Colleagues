package com.example.knowyourcolleagues.alert.dto;

import com.example.knowyourcolleagues.alert.enums.Severity;
import lombok.Data;

@Data
public class CreateAlertCommand {

    private Long ruleId;
    private Long triggerTransactionId;
    private String accountId;
    private String ruleName;
    private Severity severity;
    private String title;
    private String description;
}
