package com.example.knowyourcolleagues.dto.dashboard;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 仪表盘完整或部分数据快照；部分更新中未涉及的字段为 null。
 */
@Data
public class DashboardSnapshot {
    private Instant generatedAt;
    private DashboardSummary summary;
    private List<DashboardTransactionPoint> transactionsOverTime;
    private List<DashboardCategoryCount> alertsBySeverity;
    private List<DashboardCategoryCount> alertStatusDistribution;
    private List<DashboardResponseTimePoint> alertResponseTimeTrend;
    private List<DashboardRecentAlert> recentAlerts;
}
