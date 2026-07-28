package com.example.knowyourcolleagues.dto.dashboard;

import com.example.knowyourcolleagues.enums.AlertStatus;
import com.example.knowyourcolleagues.enums.Severity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 仪表盘最近告警表格的数据行。
 */
@Data
public class DashboardRecentAlert {
    private Long id;
    private String ruleName;
    private Severity severity;
    private String accountId;
    private BigDecimal triggerAmount;
    private String currency;
    private AlertStatus status;
    private Instant createdAt;
    private String title;
    private String description;
}
