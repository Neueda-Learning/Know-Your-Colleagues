package com.example.knowyourcolleagues.messaging;

import com.example.knowyourcolleagues.bizexception.rule.InvalidRuleRequestException;
import com.example.knowyourcolleagues.dto.RuleEngineResult;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuleEvaluationConsumerTest {

    @Mock
    private RuleEngineService ruleEngineService;
    @Mock
    private RuleEvaluationResultPublisher resultPublisher;

    @Mock
    private RuleEvaluationResultPublisher resultPublisher;

    private RuleEvaluationConsumer consumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consumer = new RuleEvaluationConsumer(
                ruleEngineService,
                resultPublisher
        );
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
        RuleEngineResult firstResult = RuleEngineResult.of(
                1001L,
                List.of(),
                List.of()
        );
        RuleEngineResult secondResult = RuleEngineResult.of(
                1002L,
                List.of(5L),
                List.of(9L)
        );
        when(ruleEngineService.evaluateTransaction(1001L))
                .thenReturn(Optional.of(firstResult));
        when(ruleEngineService.evaluateTransaction(1002L))
                .thenReturn(Optional.of(secondResult));

        consumer.consume(event);

        InOrder order = inOrder(ruleEngineService);
        order.verify(ruleEngineService).evaluateTransaction(1001L);
        order.verify(ruleEngineService).evaluateTransaction(1002L);
        verify(ruleEngineService, times(1))
                .evaluateTransaction(1001L);
        verify(resultPublisher).publish(event.getEventId(), firstResult);
        verify(resultPublisher).publish(event.getEventId(), secondResult);
    }

    @Test
    void shouldSupportLegacyEventWithOnlyTransactionId() {
        TransactionRecordedEvent event = validEvent();
        event.setTransactionId(2001L);
        RuleEngineResult result = RuleEngineResult.of(
                2001L,
                List.of(),
                List.of()
        );
        when(ruleEngineService.evaluateTransaction(2001L))
                .thenReturn(Optional.of(result));

        consumer.consume(event);

        verify(ruleEngineService).evaluateTransaction(2001L);
        verify(resultPublisher).publish(event.getEventId(), result);
    }

    @Test
    void shouldRejectEventWithoutAnyTransaction() {
        TransactionRecordedEvent event = validEvent();

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(InvalidRuleRequestException.class)
                .hasMessageContaining("no valid transaction");

        verifyNoInteractions(ruleEngineService);
        verifyNoInteractions(resultPublisher);
    }

    @Test
    void shouldRejectInvalidTransactionInsideBatch() {
        TransactionRecordedEvent event = validEvent();
        event.setTransactions(List.of(transaction(1001L), transaction(null)));

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(InvalidRuleRequestException.class)
                .hasMessageContaining("invalid transaction");

        verifyNoInteractions(ruleEngineService);
        verifyNoInteractions(resultPublisher);
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
        verifyNoInteractions(resultPublisher);
    }

    @Test
    void shouldNotPublishResultForAlreadyEvaluatedTransaction() {
        TransactionRecordedEvent event = validEvent();
        event.setTransactionId(2001L);
        when(ruleEngineService.evaluateTransaction(2001L))
                .thenReturn(Optional.empty());

        consumer.consume(event);

        verify(ruleEngineService).evaluateTransaction(2001L);
        verifyNoInteractions(resultPublisher);
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
        verifyNoInteractions(resultPublisher);
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
