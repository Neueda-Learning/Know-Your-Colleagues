package com.example.knowyourcolleagues.dto.dashboard;

import lombok.Data;

/**
 * 枚举维度及对应数量，例如告警严重级别或状态。
 */
@Data
public class DashboardCategoryCount {
    private String category;
    private Long count;

    public DashboardCategoryCount() {
    }

    public DashboardCategoryCount(String category, Long count) {
        this.category = category;
        this.count = count;
    }
}
