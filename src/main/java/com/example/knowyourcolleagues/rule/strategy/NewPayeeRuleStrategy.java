package com.example.knowyourcolleagues.rule.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.knowyourcolleagues.dto.RuleEvaluationResult;
import com.example.knowyourcolleagues.entity.Rule;
import com.example.knowyourcolleagues.entity.Transaction;
import com.example.knowyourcolleagues.enums.RuleType;
import com.example.knowyourcolleagues.enums.TransactionStatus;
import com.example.knowyourcolleagues.mapper.TransactionMapper;
import com.example.knowyourcolleagues.service.RuleEvaluationStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NewPayeeRuleStrategy implements RuleEvaluationStrategy {

    private final TransactionMapper transactionMapper;

    @Override
    public RuleType supportedType() {
        return RuleType.NEW_PAYEE;
    }

    @Override
    public RuleEvaluationResult evaluate(
            Rule rule,
            Transaction transaction
    ) {
        Long previousCount = transactionMapper.selectCount(
                new LambdaQueryWrapper<Transaction>()
                        .eq(Transaction::getAccountId,
                                transaction.getAccountId())
                        .eq(Transaction::getPayeeId,
                                transaction.getPayeeId())
                        .in(Transaction::getStatus,
                                TransactionStatus.NORMAL,
                                TransactionStatus.ABNORMAL)
                        .and(wrapper -> wrapper
                                .lt(Transaction::getTransactionTime,
                                        transaction.getTransactionTime())
                                .or(nested -> nested
                                        .eq(Transaction::getTransactionTime,
                                                transaction.getTransactionTime())
                                        .lt(Transaction::getId,
                                                transaction.getId())))
        );
        if (previousCount > 0) {
            return RuleEvaluationResult.notMatched();
        }
        return RuleEvaluationResult.matched(
                "New payee detected",
                "Account " + transaction.getAccountId()
                        + " made its first evaluated transaction to payee "
                        + transaction.getPayeeId(),
                List.of(transaction.getId())
        );
    }
}
