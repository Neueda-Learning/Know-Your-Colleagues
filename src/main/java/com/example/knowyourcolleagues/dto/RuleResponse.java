package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.RuleType;
import com.example.knowyourcolleagues.enums.Severity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class RuleResponse {

    private Long id;
    private String name;
    private String description;
    private RuleType type;
    private Severity severity;
    private Boolean enabled;
    private String currency;
    private BigDecimal thresholdAmount;
    private Integer transactionCount;
    private Integer timeWindowMinutes;
    private BigDecimal dailyLimitAmount;
    private Instant createdAt;
    private Instant updatedAt;
    private Integer version;
}
