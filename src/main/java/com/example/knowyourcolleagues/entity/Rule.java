package com.example.knowyourcolleagues.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.example.knowyourcolleagues.enums.RuleType;
import com.example.knowyourcolleagues.enums.Severity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@TableName("rules")
public class Rule {

    @TableId(type = IdType.AUTO)
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

    @Version
    private Integer version;
}
