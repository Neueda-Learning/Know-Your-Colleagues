package com.example.knowyourcolleagues.enums;

/**
 * Rule 模块完成交易规则评估后发布的结果状态。
 */
public enum TransactionEvaluationStatus {

    /**
     * 所有启用规则均未命中。
     */
    CLEARED,

    /**
     * 至少命中一条规则，并已创建相应告警。
     */
    FLAGGED
}
