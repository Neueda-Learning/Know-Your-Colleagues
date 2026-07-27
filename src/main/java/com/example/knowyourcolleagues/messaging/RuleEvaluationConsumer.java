package com.example.knowyourcolleagues.messaging;

import com.example.knowyourcolleagues.bizexception.rule.InvalidRuleRequestException;
import com.example.knowyourcolleagues.config.RuleRabbitMqConfig;
import com.example.knowyourcolleagues.dto.TransactionRecordedEvent;
import com.example.knowyourcolleagues.service.RuleEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "rule.messaging.enabled",
        havingValue = "true"
)
public class RuleEvaluationConsumer {

    private final RuleEngineService ruleEngineService;

    @RabbitListener(queues = RuleRabbitMqConfig.RULE_EVALUATION_QUEUE)
    public void consume(TransactionRecordedEvent event) {
        if (event == null
                || event.getEventId() == null
                || event.getTransactionId() == null
                || event.getOccurredAt() == null) {
            throw new InvalidRuleRequestException(
                    "Transaction event is incomplete"
            );
        }
        ruleEngineService.evaluateTransaction(event.getTransactionId());
    }
}
