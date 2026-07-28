package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.TransactionEvaluationStatus;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Rule 模块发送给 Transaction 模块的规则评估结果消息。
 */
@Data
public class TransactionEvaluationResultEvent {

    /**
     * 本条结果消息的唯一标识。
     */
    private UUID eventId;

    /**
     * 原始 transaction.recorded 消息的 eventId，便于链路追踪。
     */
    private UUID sourceEventId;

    private Long transactionId;
    private TransactionEvaluationStatus evaluationStatus;
    private List<Long> matchedRuleIds;
    private List<Long> alertIds;

    /**
     * UTC 评估完成时间。
     */
    private Instant evaluatedAt;
}
