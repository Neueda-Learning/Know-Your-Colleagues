package com.example.knowyourcolleagues.dto.dashboard;

import lombok.Data;

/**
 * 当日某个小时的交易笔数。
 */
@Data
public class DashboardTransactionPoint {
    private Integer hourOfDay;
    private String label;
    private Long transactionCount;

    public DashboardTransactionPoint() {
    }

    public DashboardTransactionPoint(
            Integer hourOfDay,
            String label,
            Long transactionCount
    ) {
        this.hourOfDay = hourOfDay;
        this.label = label;
        this.transactionCount = transactionCount;
    }
}
