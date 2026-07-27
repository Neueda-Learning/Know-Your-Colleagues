package com.example.knowyourcolleagues.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        name = "rule.messaging.enabled",
        havingValue = "true"
)
public class RuleRabbitMqConfig {

    public static final String TRANSACTION_EXCHANGE = "transaction.events";
    public static final String TRANSACTION_RECORDED_KEY =
            "transaction.recorded";
    public static final String RULE_EVALUATION_QUEUE = "rule.evaluation";

    @Bean
    DirectExchange transactionEventsExchange() {
        return new DirectExchange(TRANSACTION_EXCHANGE, true, false);
    }

    @Bean
    Queue ruleEvaluationQueue() {
        return new Queue(RULE_EVALUATION_QUEUE, true);
    }

    @Bean
    Binding ruleEvaluationBinding(
            Queue ruleEvaluationQueue,
            DirectExchange transactionEventsExchange
    ) {
        return BindingBuilder.bind(ruleEvaluationQueue)
                .to(transactionEventsExchange)
                .with(TRANSACTION_RECORDED_KEY);
    }
}
