package com.example.knowyourcolleagues.messaging;

import com.example.knowyourcolleagues.config.RuleRabbitMqConfig;
import com.example.knowyourcolleagues.dto.TransactionRecordedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "rule.messaging.enabled",
        havingValue = "true"
)
public class TransactionEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(TransactionRecordedEvent event) {
        rabbitTemplate.convertAndSend(
                RuleRabbitMqConfig.TRANSACTION_EXCHANGE,
                RuleRabbitMqConfig.TRANSACTION_RECORDED_KEY,
                event
        );
    }
}
