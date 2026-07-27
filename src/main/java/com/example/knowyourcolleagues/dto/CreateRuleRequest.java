package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.RuleType;
import com.example.knowyourcolleagues.enums.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "创建监控规则的请求")
public class CreateRuleRequest {

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
}
