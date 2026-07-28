package com.example.knowyourcolleagues.messaging;

import com.example.knowyourcolleagues.config.RuleRabbitMqConfig;
import com.example.knowyourcolleagues.dto.RuleEngineResult;
import com.example.knowyourcolleagues.dto.TransactionEvaluationResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 发布单笔交易的最终规则评估结果。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "transaction.messaging.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RuleEvaluationResultPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(UUID sourceEventId, RuleEngineResult result) {
        if (sourceEventId == null || result == null) {
            throw new IllegalArgumentException(
                    "Source event and rule evaluation result are required"
            );
        }

        TransactionEvaluationResultEvent event =
                new TransactionEvaluationResultEvent();
        event.setEventId(UUID.randomUUID());
        event.setSourceEventId(sourceEventId);
        event.setTransactionId(result.getTransactionId());
        event.setEvaluationStatus(result.getEvaluationStatus());
        event.setMatchedRuleIds(result.getMatchedRuleIds());
        event.setAlertIds(result.getAlertIds());
        event.setEvaluatedAt(Instant.now());

        rabbitTemplate.convertAndSend(
                RuleRabbitMqConfig.RULE_EVALUATION_RESULTS_EXCHANGE,
                RuleRabbitMqConfig.TRANSACTION_EVALUATED_KEY,
                event
        );

        log.info(
                "Published rule evaluation result: eventId={}, "
                        + "sourceEventId={}, transactionId={}, status={}, "
                        + "matchedRuleCount={}",
                event.getEventId(),
                sourceEventId,
                result.getTransactionId(),
                result.getEvaluationStatus(),
                result.getMatchedRuleIds().size()
        );
    }
}
