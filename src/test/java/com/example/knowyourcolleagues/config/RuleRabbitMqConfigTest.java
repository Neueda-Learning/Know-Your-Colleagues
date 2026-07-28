package com.example.knowyourcolleagues.config;

import com.example.knowyourcolleagues.dto.TransactionRecordedEvent;
import com.example.knowyourcolleagues.dto.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuleRabbitMqConfigTest {

    @Test
    void shouldSerializeAndDeserializeTransactionListAsJson() {
        TransactionResponse transaction = new TransactionResponse();
        transaction.setId(1001L);
        transaction.setTransactionRef("TXN-TEST-001");
        transaction.setAccountId("ACC-001");
        transaction.setPayeeId("PAYEE-001");
        transaction.setAmount(new BigDecimal("128.88"));
        transaction.setCurrency("USD");

        TransactionRecordedEvent event = new TransactionRecordedEvent();
        event.setEventId(UUID.randomUUID());
        event.setTransactionId(transaction.getId());
        event.setTransactions(List.of(transaction));
        event.setOccurredAt(Instant.parse("2026-07-28T00:00:00Z"));

        JacksonJsonMessageConverter converter =
                new RuleRabbitMqConfig().rabbitJsonMessageConverter();
        Message message = converter.toMessage(
                event,
                new MessageProperties()
        );

        assertThat(message.getMessageProperties().getContentType())
                .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(new String(message.getBody()))
                .contains("\"transactions\"")
                .contains("\"TXN-TEST-001\"");

        Object converted = converter.fromMessage(message);
        assertThat(converted).isInstanceOf(TransactionRecordedEvent.class);

        TransactionRecordedEvent received =
                (TransactionRecordedEvent) converted;
        assertThat(received.getTransactions()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getId()).isEqualTo(1001L);
                    assertThat(item.getTransactionRef())
                            .isEqualTo("TXN-TEST-001");
                    assertThat(item.getAmount())
                            .isEqualByComparingTo("128.88");
                });
    }
}
