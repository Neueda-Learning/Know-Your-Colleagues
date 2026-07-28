package com.example.knowyourcolleagues.messaging;

import com.example.knowyourcolleagues.bizexception.rule.InvalidRuleRequestException;
import com.example.knowyourcolleagues.dto.TransactionRecordedEvent;
import com.example.knowyourcolleagues.dto.TransactionResponse;
import com.example.knowyourcolleagues.service.RuleEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RuleEvaluationConsumerTest {

    @Mock
    private RuleEngineService ruleEngineService;

    private RuleEvaluationConsumer consumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consumer = new RuleEvaluationConsumer(ruleEngineService);
    }

    @Test
    void shouldEvaluateEveryDistinctTransactionInBatchOrder() {
        TransactionRecordedEvent event = validEvent();
        event.setTransactionId(1001L);
        event.setTransactions(List.of(
                transaction(1001L),
                transaction(1002L),
                transaction(1001L)
        ));

        consumer.consume(event);

        InOrder order = inOrder(ruleEngineService);
        order.verify(ruleEngineService).evaluateTransaction(1001L);
        order.verify(ruleEngineService).evaluateTransaction(1002L);
        verify(ruleEngineService, times(1))
                .evaluateTransaction(1001L);
    }

    @Test
    void shouldSupportLegacyEventWithOnlyTransactionId() {
        TransactionRecordedEvent event = validEvent();
        event.setTransactionId(2001L);

        consumer.consume(event);

        verify(ruleEngineService).evaluateTransaction(2001L);
    }

    @Test
    void shouldRejectEventWithoutAnyTransaction() {
        TransactionRecordedEvent event = validEvent();

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(InvalidRuleRequestException.class)
                .hasMessageContaining("no valid transaction");

        verifyNoInteractions(ruleEngineService);
    }

    @Test
    void shouldRejectInvalidTransactionInsideBatch() {
        TransactionRecordedEvent event = validEvent();
        event.setTransactions(List.of(transaction(1001L), transaction(null)));

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(InvalidRuleRequestException.class)
                .hasMessageContaining("invalid transaction");

        verifyNoInteractions(ruleEngineService);
    }

    @Test
    void shouldRejectEventWithoutTrackingMetadata() {
        TransactionRecordedEvent event = validEvent();
        event.setEventId(null);
        event.setTransactionId(1001L);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(InvalidRuleRequestException.class)
                .hasMessageContaining("incomplete");

        verifyNoInteractions(ruleEngineService);
    }

    @Test
    void shouldPropagateRuleEvaluationFailureForRabbitRetry() {
        TransactionRecordedEvent event = validEvent();
        event.setTransactions(List.of(transaction(1001L)));
        doThrow(new IllegalStateException("evaluation failed"))
                .when(ruleEngineService)
                .evaluateTransaction(1001L);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evaluation failed");
    }

    private TransactionRecordedEvent validEvent() {
        TransactionRecordedEvent event = new TransactionRecordedEvent();
        event.setEventId(UUID.randomUUID());
        event.setOccurredAt(Instant.parse("2026-07-28T00:00:00Z"));
        return event;
    }

    private TransactionResponse transaction(Long transactionId) {
        TransactionResponse transaction = new TransactionResponse();
        transaction.setId(transactionId);
        return transaction;
    }
}
