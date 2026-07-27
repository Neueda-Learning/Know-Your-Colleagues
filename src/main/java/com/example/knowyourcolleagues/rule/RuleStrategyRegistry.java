package com.example.knowyourcolleagues.rule;

import com.example.knowyourcolleagues.bizexception.rule.UnsupportedRuleTypeException;
import com.example.knowyourcolleagues.enums.RuleType;
import com.example.knowyourcolleagues.service.RuleEvaluationStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class RuleStrategyRegistry {

    private final Map<RuleType, RuleEvaluationStrategy> strategies;

    public RuleStrategyRegistry(List<RuleEvaluationStrategy> strategyList) {
        Map<RuleType, RuleEvaluationStrategy> registry =
                new EnumMap<>(RuleType.class);
        for (RuleEvaluationStrategy strategy : strategyList) {
            RuleEvaluationStrategy duplicate = registry.put(
                    strategy.supportedType(),
                    strategy
            );
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Duplicate strategy for " + strategy.supportedType()
                );
            }
        }
        this.strategies = Map.copyOf(registry);
    }

    public RuleEvaluationStrategy get(RuleType type) {
        RuleEvaluationStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new UnsupportedRuleTypeException(
                    "No strategy registered for rule type: " + type
            );
        }
        return strategy;
    }
}
