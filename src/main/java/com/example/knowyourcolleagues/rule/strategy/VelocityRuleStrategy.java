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

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VelocityRuleStrategy implements RuleEvaluationStrategy {

    private final TransactionMapper transactionMapper;

    @Override
    public RuleType supportedType() {
        return RuleType.VELOCITY;
    }

    @Override
    public RuleEvaluationResult evaluate(
            Rule rule,
            Transaction transaction
    ) {
        LocalDateTime start = transaction.getTransactionTime()
                .minusMinutes(rule.getTimeWindowMinutes());
        List<Transaction> transactions = transactionMapper.selectList(
                new LambdaQueryWrapper<Transaction>()
                        .eq(Transaction::getAccountId,
                                transaction.getAccountId())
                        .eq(Transaction::getStatus,
                                TransactionStatus.COMPLETED)
                        .ge(Transaction::getTransactionTime, start)
                        .le(Transaction::getTransactionTime,
                                transaction.getTransactionTime())
                        .orderByAsc(Transaction::getTransactionTime)
                        .orderByAsc(Transaction::getId)
        ).stream().filter(item -> isNotAfter(item, transaction)).toList();

        if (transactions.size() != rule.getTransactionCount() + 1) {
            return RuleEvaluationResult.notMatched();
        }
        return RuleEvaluationResult.matched(
                "Transaction velocity exceeded",
                transactions.size() + " completed transactions occurred "
                        + "within " + rule.getTimeWindowMinutes()
                        + " minutes for account "
                        + transaction.getAccountId(),
                transactions.stream().map(Transaction::getId).toList()
        );
    }

    private boolean isNotAfter(
            Transaction candidate,
            Transaction current
    ) {
        return candidate.getTransactionTime()
                .isBefore(current.getTransactionTime())
                || (candidate.getTransactionTime()
                        .equals(current.getTransactionTime())
                        && candidate.getId() <= current.getId());
    }
}
