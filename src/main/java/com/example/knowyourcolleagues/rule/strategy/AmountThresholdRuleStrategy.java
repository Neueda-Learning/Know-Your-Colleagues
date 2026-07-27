package com.example.knowyourcolleagues.rule.strategy;

import com.example.knowyourcolleagues.dto.RuleEvaluationResult;
import com.example.knowyourcolleagues.entity.Rule;
import com.example.knowyourcolleagues.entity.Transaction;
import com.example.knowyourcolleagues.enums.RuleType;
import com.example.knowyourcolleagues.service.RuleEvaluationStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AmountThresholdRuleStrategy
        implements RuleEvaluationStrategy {

    @Override
    public RuleType supportedType() {
        return RuleType.AMOUNT_THRESHOLD;
    }

    @Override
    public RuleEvaluationResult evaluate(
            Rule rule,
            Transaction transaction
    ) {
        if (!currencyMatches(rule, transaction)
                || transaction.getAmount().compareTo(
                        rule.getThresholdAmount()
                ) <= 0) {
            return RuleEvaluationResult.notMatched();
        }
        return RuleEvaluationResult.matched(
                "Amount threshold exceeded",
                "Transaction amount " + transaction.getAmount()
                        + " " + transaction.getCurrency()
                        + " exceeded threshold "
                        + rule.getThresholdAmount(),
                List.of(transaction.getId())
        );
    }

    private boolean currencyMatches(Rule rule, Transaction transaction) {
        return rule.getCurrency() == null
                || rule.getCurrency().equalsIgnoreCase(
                        transaction.getCurrency()
                );
    }
}
