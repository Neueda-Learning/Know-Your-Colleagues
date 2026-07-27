package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "修改规则的请求；规则类型创建后不可修改")
public class UpdateRuleRequest {

    private String name;
    private String description;
    private Severity severity;
    private String currency;
    private BigDecimal thresholdAmount;
    private Integer transactionCount;
    private Integer timeWindowMinutes;
    private BigDecimal dailyLimitAmount;
    private Integer version;
}
