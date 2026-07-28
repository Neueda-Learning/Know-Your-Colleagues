package com.example.knowyourcolleagues.messaging;

import com.example.knowyourcolleagues.config.RuleRabbitMqConfig;
import com.example.knowyourcolleagues.dto.TransactionRecordedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "transaction.messaging.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TransactionEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(TransactionRecordedEvent event) {
        if (event == null
                || event.getTransactions() == null
                || event.getTransactions().isEmpty()) {
            throw new IllegalArgumentException(
                    "Transaction event must contain at least one transaction"
            );
        }

        rabbitTemplate.convertAndSend(
                RuleRabbitMqConfig.TRANSACTION_EXCHANGE,
                RuleRabbitMqConfig.TRANSACTION_RECORDED_KEY,
                event
        );

        log.info(
                "Published transaction event: eventId={}, transactionCount={}",
                event.getEventId(),
                event.getTransactions().size()
        );
    }
}
