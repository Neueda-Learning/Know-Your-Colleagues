package com.example.knowyourcolleagues.messaging;

import com.example.knowyourcolleagues.bizexception.rule.InvalidRuleRequestException;
import com.example.knowyourcolleagues.config.RuleRabbitMqConfig;
import com.example.knowyourcolleagues.dto.TransactionRecordedEvent;
import com.example.knowyourcolleagues.dto.TransactionResponse;
import com.example.knowyourcolleagues.dto.RuleEngineResult;
import com.example.knowyourcolleagues.service.RuleEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "transaction.messaging.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RuleEvaluationConsumer {

    private final RuleEngineService ruleEngineService;
    private final RuleEvaluationResultPublisher resultPublisher;

    @RabbitListener(queues = RuleRabbitMqConfig.RULE_EVALUATION_QUEUE)
    public void consume(TransactionRecordedEvent event) {
        if (event == null
                || event.getEventId() == null
                || event.getOccurredAt() == null) {
            throw new InvalidRuleRequestException(
                    "Transaction event is incomplete"
            );
        }

        Set<Long> transactionIds = extractTransactionIds(event);
        for (Long transactionId : transactionIds) {
            ruleEngineService.evaluateTransaction(transactionId)
                    .ifPresent(result -> publishResult(event, result));
        }

        log.info(
                "Evaluated transaction event: eventId={}, "
                        + "transactionCount={}",
                event.getEventId(),
                transactionIds.size()
        );
    }

    private void publishResult(
            TransactionRecordedEvent sourceEvent,
            RuleEngineResult result
    ) {
        resultPublisher.publish(sourceEvent.getEventId(), result);
    }

    private Set<Long> extractTransactionIds(
            TransactionRecordedEvent event
    ) {
        LinkedHashSet<Long> transactionIds = new LinkedHashSet<>();
        List<TransactionResponse> transactions = event.getTransactions();

        if (transactions != null && !transactions.isEmpty()) {
            for (TransactionResponse transaction : transactions) {
                if (transaction == null
                        || transaction.getId() == null
                        || transaction.getId() <= 0) {
                    throw new InvalidRuleRequestException(
                            "Transaction event contains an invalid transaction"
                    );
                }
                transactionIds.add(transaction.getId());
            }
            return transactionIds;
        }

        if (event.getTransactionId() == null
                || event.getTransactionId() <= 0) {
            throw new InvalidRuleRequestException(
                    "Transaction event contains no valid transaction"
            );
        }
        transactionIds.add(event.getTransactionId());
        return transactionIds;
    }
}
