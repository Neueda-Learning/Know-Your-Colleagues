package com.example.knowyourcolleagues.rule.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.knowyourcolleagues.dto.RuleEvaluationResult;
import com.example.knowyourcolleagues.entity.Rule;
import com.example.knowyourcolleagues.entity.Transaction;
import com.example.knowyourcolleagues.enums.RuleType;
import com.example.knowyourcolleagues.enums.TransactionStatus;
import com.example.knowyourcolleagues.enums.TransactionType;
import com.example.knowyourcolleagues.mapper.TransactionMapper;
import com.example.knowyourcolleagues.service.RuleEvaluationStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DailyLimitRuleStrategy implements RuleEvaluationStrategy {

    private final TransactionMapper transactionMapper;

    @Override
    public RuleType supportedType() {
        return RuleType.DAILY_LIMIT;
    }

    @Override
    public RuleEvaluationResult evaluate(
            Rule rule,
            Transaction transaction
    ) {
        if (transaction.getTransactionType() != TransactionType.DEBIT
                || !currencyMatches(rule, transaction)) {
            return RuleEvaluationResult.notMatched();
        }

        LocalDateTime start = transaction.getTransactionTime()
                .toLocalDate()
                .atStartOfDay();
        List<Transaction> transactions = transactionMapper.selectList(
                new LambdaQueryWrapper<Transaction>()
                        .eq(Transaction::getAccountId,
                                transaction.getAccountId())
                        .eq(Transaction::getCurrency,
                                transaction.getCurrency())
                        .eq(Transaction::getTransactionType,
                                TransactionType.DEBIT)
                        .eq(Transaction::getStatus,
                                TransactionStatus.COMPLETED)
                        .ge(Transaction::getTransactionTime, start)
                        .le(Transaction::getTransactionTime,
                                transaction.getTransactionTime())
                        .orderByAsc(Transaction::getTransactionTime)
                        .orderByAsc(Transaction::getId)
        ).stream().filter(item -> isNotAfter(item, transaction)).toList();

        BigDecimal currentTotal = transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousTotal = currentTotal.subtract(
                transaction.getAmount()
        );
        if (previousTotal.compareTo(rule.getDailyLimitAmount()) > 0
                || currentTotal.compareTo(rule.getDailyLimitAmount()) <= 0) {
            return RuleEvaluationResult.notMatched();
        }

        return RuleEvaluationResult.matched(
                "Daily transaction limit exceeded",
                "Daily completed debit total " + currentTotal + " "
                        + transaction.getCurrency() + " exceeded limit "
                        + rule.getDailyLimitAmount(),
                transactions.stream().map(Transaction::getId).toList()
        );
    }

    private boolean currencyMatches(Rule rule, Transaction transaction) {
        return rule.getCurrency() == null
                || rule.getCurrency().equalsIgnoreCase(
                        transaction.getCurrency()
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
