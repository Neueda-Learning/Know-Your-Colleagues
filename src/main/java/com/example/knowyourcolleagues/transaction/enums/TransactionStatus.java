package com.example.knowyourcolleagues.transaction.enums;

/**
 * 交易处理状态。
 */
public enum TransactionStatus {

    /**
     * 交易已创建，正在等待处理。
     */
    PENDING,

    /**
     * 交易已经成功完成，可参与规则评估。
     */
    COMPLETED,

    /**
     * 交易处理失败，不参与正常的规则统计。
     */
    FAILED
}
