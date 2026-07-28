package com.example.knowyourcolleagues.dto.dashboard;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 某日告警首次响应时长均值。
 */
@Data
public class DashboardResponseTimePoint {
    private LocalDate date;
    private String label;
    private BigDecimal averageMinutes;

    public DashboardResponseTimePoint() {
    }

    public DashboardResponseTimePoint(
            LocalDate date,
            String label,
            BigDecimal averageMinutes
    ) {
        this.date = date;
        this.label = label;
        this.averageMinutes = averageMinutes;
    }
}
