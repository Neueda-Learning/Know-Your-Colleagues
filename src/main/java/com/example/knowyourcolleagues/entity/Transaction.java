package com.example.knowyourcolleagues.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.knowyourcolleagues.enums.TransactionStatus;
import com.example.knowyourcolleagues.enums.TransactionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易实体，对应数据库中的 transactions 表。
 */
@Data
@TableName("transactions")
public class Transaction {

    /**
     * 交易主键，由数据库自动生成。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 后端生成的唯一交易号，包含 UTC 时间和随机部分。
     */
    private String transactionRef;

    /**
     * 发起交易的账户标识。
     */
    private String accountId;

    /**
     * 收款人或交易对手标识。
     */
    private String payeeId;

    /**
     * 交易金额，使用高精度数值避免浮点计算误差。
     */
    private BigDecimal amount;

    /**
     * ISO 4217 三位币种代码，例如 USD、CNY。
     */
    private String currency;

    /**
     * 交易类型，例如借记或贷记。
     */
    private TransactionType transactionType;

    /**
     * 当前交易状态。
     */
    private TransactionStatus status;

    /**
     * 交易描述或附言。
     */
    private String description;

    /**
     * 交易实际发生时间，系统内部统一按 UTC 处理。
     */
    private LocalDateTime transactionTime;

    /**
     * 交易记录创建时间，系统内部统一按 UTC 处理。
     */
    private LocalDateTime createdAt;

    /**
     * 交易记录最后更新时间，系统内部统一按 UTC 处理。
     */
    private LocalDateTime updatedAt;
}
