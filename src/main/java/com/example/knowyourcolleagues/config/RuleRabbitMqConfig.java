package com.example.knowyourcolleagues.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RuleRabbitMqConfig {

    public static final String TRANSACTION_EXCHANGE = "transaction.events";
    public static final String TRANSACTION_RECORDED_KEY =
            "transaction.recorded";
    public static final String RULE_EVALUATION_QUEUE = "rule.evaluation";
    public static final String RULE_EVALUATION_RESULTS_EXCHANGE =
            "rule.evaluation.results";
    public static final String TRANSACTION_EVALUATED_KEY =
            "transaction.evaluated";
    public static final String TRANSACTION_STATUS_UPDATE_QUEUE =
            "transaction.status.update";

    /**
     * 使用 JSON 传递交易事件，确保包含交易列表的复杂对象可以被消费者反序列化。
     */
    @Bean
    JacksonJsonMessageConverter rabbitJsonMessageConverter() {
        return new JacksonJsonMessageConverter(
                "com.example.knowyourcolleagues.dto"
        );
    }

    @Bean
    DirectExchange transactionEventsExchange() {
        return new DirectExchange(TRANSACTION_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange ruleEvaluationResultsExchange() {
        return new DirectExchange(
                RULE_EVALUATION_RESULTS_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    Queue ruleEvaluationQueue() {
        return new Queue(RULE_EVALUATION_QUEUE, true);
    }

    @Bean
    Queue transactionStatusUpdateQueue() {
        return new Queue(TRANSACTION_STATUS_UPDATE_QUEUE, true);
    }

    @Bean
    Binding ruleEvaluationBinding(
            @Qualifier("ruleEvaluationQueue") Queue ruleEvaluationQueue,
            @Qualifier("transactionEventsExchange")
            DirectExchange transactionEventsExchange
    ) {
        return BindingBuilder.bind(ruleEvaluationQueue)
                .to(transactionEventsExchange)
                .with(TRANSACTION_RECORDED_KEY);
    }

    @Bean
    Binding transactionStatusUpdateBinding(
            @Qualifier("transactionStatusUpdateQueue")
            Queue transactionStatusUpdateQueue,
            @Qualifier("ruleEvaluationResultsExchange")
            DirectExchange ruleEvaluationResultsExchange
    ) {
        return BindingBuilder.bind(transactionStatusUpdateQueue)
                .to(ruleEvaluationResultsExchange)
                .with(TRANSACTION_EVALUATED_KEY);
    }
}
