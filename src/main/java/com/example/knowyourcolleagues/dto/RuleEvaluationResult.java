package com.example.knowyourcolleagues.dto;

import lombok.Value;

import java.util.List;

@Value
public class RuleEvaluationResult {

    boolean matched;
    String title;
    String description;
    List<Long> relatedTransactionIds;

    public static RuleEvaluationResult notMatched() {
        return new RuleEvaluationResult(false, null, null, List.of());
    }

    public static RuleEvaluationResult matched(
            String title,
            String description,
            List<Long> relatedTransactionIds
    ) {
        return new RuleEvaluationResult(
                true,
                title,
                description,
                List.copyOf(relatedTransactionIds)
        );
    }
}
