package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.TransactionEvaluationStatus;
import lombok.Value;

import java.util.List;

/**
 * 单笔交易完成全部启用规则评估后的汇总结果。
 */
@Value
public class RuleEngineResult {

    Long transactionId;
    TransactionEvaluationStatus evaluationStatus;
    List<Long> matchedRuleIds;
    List<Long> alertIds;

    public static RuleEngineResult of(
            Long transactionId,
            List<Long> matchedRuleIds,
            List<Long> alertIds
    ) {
        List<Long> safeRuleIds = List.copyOf(matchedRuleIds);
        List<Long> safeAlertIds = List.copyOf(alertIds);
        TransactionEvaluationStatus status = safeRuleIds.isEmpty()
                ? TransactionEvaluationStatus.CLEARED
                : TransactionEvaluationStatus.FLAGGED;
        return new RuleEngineResult(
                transactionId,
                status,
                safeRuleIds,
                safeAlertIds
        );
    }
}
