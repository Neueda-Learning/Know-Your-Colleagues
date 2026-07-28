package com.example.knowyourcolleagues.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 交易成功落库后发布到 RabbitMQ 的事件。
 *
 * <p>{@code transactions} 使用列表结构，即使当前接口每次只创建一笔交易，
 * 消费者也可以统一按批次遍历处理，后续扩展批量交易时无需更改消息结构。</p>
 */
@Data
public class TransactionRecordedEvent {

    /** 事件唯一标识，用于日志追踪和后续幂等处理。 */
    private UUID eventId;

    /** 本次创建的首笔交易主键，保留该字段以兼容已有规则消费者。 */
    private Long transactionId;

    /** 已成功落库、等待规则判断的交易列表。 */
    private List<TransactionResponse> transactions;

    /** 事件发生时间，使用 UTC。 */
    private Instant occurredAt;
}
