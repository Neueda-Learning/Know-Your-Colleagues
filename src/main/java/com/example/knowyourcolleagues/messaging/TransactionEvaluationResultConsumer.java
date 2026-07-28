package com.example.knowyourcolleagues.messaging;

import com.example.knowyourcolleagues.bizexception.transaction.InvalidTransactionRequestException;
import com.example.knowyourcolleagues.config.RuleRabbitMqConfig;
import com.example.knowyourcolleagues.dto.TransactionEvaluationResultEvent;
import com.example.knowyourcolleagues.enums.TransactionEvaluationStatus;
import com.example.knowyourcolleagues.enums.TransactionStatus;
import com.example.knowyourcolleagues.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费规则评估结果，并将交易从 PENDING 更新为最终状态。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "transaction.messaging.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TransactionEvaluationResultConsumer {

    private final TransactionService transactionService;

    @RabbitListener(
            queues = RuleRabbitMqConfig.TRANSACTION_STATUS_UPDATE_QUEUE
    )
    public void consume(TransactionEvaluationResultEvent event) {
        validate(event);

        TransactionStatus targetStatus = switch (
                event.getEvaluationStatus()
        ) {
            case CLEARED -> TransactionStatus.NORMAL;
            case FLAGGED -> TransactionStatus.ABNORMAL;
        };
        transactionService.updateStatusAfterEvaluation(
                event.getTransactionId(),
                targetStatus
        );

        log.info(
                "Updated transaction from rule evaluation result: "
                        + "eventId={}, sourceEventId={}, "
                        + "transactionId={}, status={}",
                event.getEventId(),
                event.getSourceEventId(),
                event.getTransactionId(),
                targetStatus
        );
    }

    private void validate(TransactionEvaluationResultEvent event) {
        if (event == null
                || event.getEventId() == null
                || event.getSourceEventId() == null
                || event.getTransactionId() == null
                || event.getTransactionId() <= 0
                || event.getEvaluationStatus() == null
                || event.getEvaluatedAt() == null) {
            throw new InvalidTransactionRequestException(
                    "Rule evaluation result event is incomplete"
            );
        }
        if (event.getEvaluationStatus()
                != TransactionEvaluationStatus.CLEARED
                && event.getEvaluationStatus()
                != TransactionEvaluationStatus.FLAGGED) {
            throw new InvalidTransactionRequestException(
                    "Unsupported rule evaluation status"
            );
        }
    }
}
