package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.Severity;
import lombok.Data;

import java.util.List;

@Data
public class CreateAlertCommand {

    private Long ruleId;
    private Long triggerTransactionId;
    private String accountId;
    private String ruleName;
    private Severity severity;
    private String title;
    private String description;
    private List<Long> relatedTransactionIds;
}
