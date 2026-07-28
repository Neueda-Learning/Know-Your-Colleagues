package com.example.knowyourcolleagues.dto.dashboard;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 仪表盘顶部汇总指标。
 */
@Data
public class DashboardSummary {
    private Long openAlerts;
    private Long acknowledgedAlerts;
    private Long totalAlertsToday;
    private BigDecimal alertsTodayChangePercent;
    private BigDecimal averageResolutionMinutes;
    private BigDecimal targetResolutionMinutes;
}
