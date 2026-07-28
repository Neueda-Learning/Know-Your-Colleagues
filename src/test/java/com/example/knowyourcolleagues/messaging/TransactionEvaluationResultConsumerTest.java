package com.example.knowyourcolleagues.messaging;

import com.example.knowyourcolleagues.bizexception.transaction.InvalidTransactionRequestException;
import com.example.knowyourcolleagues.dto.TransactionEvaluationResultEvent;
import com.example.knowyourcolleagues.enums.TransactionEvaluationStatus;
import com.example.knowyourcolleagues.enums.TransactionStatus;
import com.example.knowyourcolleagues.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TransactionEvaluationResultConsumerTest {

    @Mock
    private TransactionService transactionService;

    private TransactionEvaluationResultConsumer consumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consumer = new TransactionEvaluationResultConsumer(
                transactionService
        );
    }

    @Test
    void shouldMapClearedResultToNormalTransaction() {
        TransactionEvaluationResultEvent event = validEvent();
        event.setEvaluationStatus(TransactionEvaluationStatus.CLEARED);

        consumer.consume(event);

        verify(transactionService).updateStatusAfterEvaluation(
                1001L,
                TransactionStatus.NORMAL
        );
    }

    @Test
    void shouldMapFlaggedResultToAbnormalTransaction() {
        TransactionEvaluationResultEvent event = validEvent();
        event.setEvaluationStatus(TransactionEvaluationStatus.FLAGGED);

        consumer.consume(event);

        verify(transactionService).updateStatusAfterEvaluation(
                1001L,
                TransactionStatus.ABNORMAL
        );
    }

    @Test
    void shouldRejectIncompleteResultEvent() {
        TransactionEvaluationResultEvent event = validEvent();
        event.setSourceEventId(null);

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(InvalidTransactionRequestException.class)
                .hasMessageContaining("incomplete");

        verifyNoInteractions(transactionService);
    }

    private TransactionEvaluationResultEvent validEvent() {
        TransactionEvaluationResultEvent event =
                new TransactionEvaluationResultEvent();
        event.setEventId(UUID.randomUUID());
        event.setSourceEventId(UUID.randomUUID());
        event.setTransactionId(1001L);
        event.setEvaluationStatus(TransactionEvaluationStatus.CLEARED);
        event.setEvaluatedAt(Instant.parse("2026-07-28T00:00:00Z"));
        return event;
    }
}
