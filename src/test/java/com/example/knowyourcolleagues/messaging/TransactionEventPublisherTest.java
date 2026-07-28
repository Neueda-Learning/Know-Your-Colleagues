package com.example.knowyourcolleagues.messaging;

import com.example.knowyourcolleagues.config.RuleRabbitMqConfig;
import com.example.knowyourcolleagues.dto.TransactionRecordedEvent;
import com.example.knowyourcolleagues.dto.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

class TransactionEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private TransactionEventPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        publisher = new TransactionEventPublisher(rabbitTemplate);
    }

    @Test
    void shouldPublishTransactionListToRuleEvaluationRoute() {
        TransactionResponse transaction = new TransactionResponse();
        transaction.setId(1001L);

        TransactionRecordedEvent event = new TransactionRecordedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTransactionId(transaction.getId());
        event.setTransactions(List.of(transaction));
        event.setOccurredAt(Instant.parse("2026-07-28T00:00:00Z"));

        publisher.publish(event);

        verify(rabbitTemplate).convertAndSend(
                RuleRabbitMqConfig.TRANSACTION_EXCHANGE,
                RuleRabbitMqConfig.TRANSACTION_RECORDED_KEY,
                event
        );
    }

    @Test
    void shouldRejectEventWithoutTransactions() {
        TransactionRecordedEvent event = new TransactionRecordedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTransactions(List.of());
        event.setOccurredAt(Instant.parse("2026-07-28T00:00:00Z"));

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one transaction");
    }
}
