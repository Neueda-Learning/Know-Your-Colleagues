package com.example.knowyourcolleagues.messaging;

import com.example.knowyourcolleagues.config.RuleRabbitMqConfig;
import com.example.knowyourcolleagues.dto.RuleEngineResult;
import com.example.knowyourcolleagues.dto.TransactionEvaluationResultEvent;
import com.example.knowyourcolleagues.enums.TransactionEvaluationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class RuleEvaluationResultPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RuleEvaluationResultPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        publisher = new RuleEvaluationResultPublisher(rabbitTemplate);
    }

    @Test
    void shouldPublishFlaggedResultToTransactionQueueRoute() {
        UUID sourceEventId = UUID.randomUUID();
        RuleEngineResult result = RuleEngineResult.of(
                1001L,
                List.of(5L, 8L),
                List.of(20L, 21L)
        );

        publisher.publish(sourceEventId, result);

        ArgumentCaptor<TransactionEvaluationResultEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        TransactionEvaluationResultEvent.class
                );
        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(
                        RuleRabbitMqConfig
                                .RULE_EVALUATION_RESULTS_EXCHANGE
                ),
                org.mockito.ArgumentMatchers.eq(
                        RuleRabbitMqConfig.TRANSACTION_EVALUATED_KEY
                ),
                eventCaptor.capture()
        );

        TransactionEvaluationResultEvent event = eventCaptor.getValue();
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getSourceEventId()).isEqualTo(sourceEventId);
        assertThat(event.getTransactionId()).isEqualTo(1001L);
        assertThat(event.getEvaluationStatus())
                .isEqualTo(TransactionEvaluationStatus.FLAGGED);
        assertThat(event.getMatchedRuleIds()).containsExactly(5L, 8L);
        assertThat(event.getAlertIds()).containsExactly(20L, 21L);
        assertThat(event.getEvaluatedAt()).isNotNull();
    }

    @Test
    void shouldPublishClearedResultWithoutMatches() {
        UUID sourceEventId = UUID.randomUUID();

        publisher.publish(
                sourceEventId,
                RuleEngineResult.of(1002L, List.of(), List.of())
        );

        ArgumentCaptor<TransactionEvaluationResultEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        TransactionEvaluationResultEvent.class
                );
        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                eventCaptor.capture()
        );
        assertThat(eventCaptor.getValue().getEvaluationStatus())
                .isEqualTo(TransactionEvaluationStatus.CLEARED);
        assertThat(eventCaptor.getValue().getMatchedRuleIds()).isEmpty();
        assertThat(eventCaptor.getValue().getAlertIds()).isEmpty();
    }
}
