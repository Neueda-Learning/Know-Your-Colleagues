package com.example.knowyourcolleagues.enums;

/**
 * 交易处理状态。
 */
public enum TransactionStatus {

    /**
     * 标记为正常交易。
     */
    NORMAL,

    /**
     * 交易已经创建，正在等待规则校验
     */
    PENDING,

    /**
     * 规则校验失败，标记为异常交易。
     */
    ABNORMAL
}
