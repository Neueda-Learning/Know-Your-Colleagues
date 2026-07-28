package com.example.knowyourcolleagues.dto.dashboard;

import lombok.Data;

/**
 * 仪表盘运营汇总查询的数据库投影。
 */
@Data
public class DashboardOperationalSummaryRow {
    private Long openAlerts;
    private Long acknowledgedAlerts;
    private Long totalAlertsToday;
    private Long totalAlertsYesterday;
}
