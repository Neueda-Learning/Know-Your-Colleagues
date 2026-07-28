package com.example.knowyourcolleagues.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.knowyourcolleagues.bizexception.rule.InvalidRuleRequestException;
import com.example.knowyourcolleagues.bizexception.transaction.TransactionNotFoundException;
import com.example.knowyourcolleagues.dto.CreateAlertCommand;
import com.example.knowyourcolleagues.dto.AlertResponse;
import com.example.knowyourcolleagues.dto.RuleEngineResult;
import com.example.knowyourcolleagues.dto.RuleEvaluationResult;
import com.example.knowyourcolleagues.entity.Rule;
import com.example.knowyourcolleagues.entity.Transaction;
import com.example.knowyourcolleagues.enums.TransactionStatus;
import com.example.knowyourcolleagues.mapper.RuleMapper;
import com.example.knowyourcolleagues.mapper.TransactionMapper;
import com.example.knowyourcolleagues.rule.RuleStrategyRegistry;
import com.example.knowyourcolleagues.service.AlertService;
import com.example.knowyourcolleagues.service.RuleEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RuleEngineServiceImpl implements RuleEngineService {

    private final RuleMapper ruleMapper;
    private final TransactionMapper transactionMapper;
    private final RuleStrategyRegistry strategyRegistry;
    private final AlertService alertService;

    @Override
    @Transactional
    public Optional<RuleEngineResult> evaluateTransaction(Long transactionId) {
        if (transactionId == null || transactionId <= 0) {
            throw new InvalidRuleRequestException(
                    "transactionId must be positive"
            );
        }
        Transaction transaction = transactionMapper.selectById(transactionId);
        if (transaction == null) {
            throw new TransactionNotFoundException(
                    "Transaction not found: " + transactionId
            );
        }
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            return Optional.empty();
        }

        List<Rule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<Rule>()
                        .eq(Rule::getEnabled, Boolean.TRUE)
                        .orderByAsc(Rule::getId)
        );
        List<Long> matchedRuleIds = new ArrayList<>();
        List<Long> alertIds = new ArrayList<>();
        for (Rule rule : rules) {
            RuleEvaluationResult result = strategyRegistry
                    .get(rule.getType())
                    .evaluate(rule, transaction);
            if (result.isMatched()) {
                AlertResponse alert = alertService.createAlert(
                        toAlertCommand(rule, transaction, result)
                );
                matchedRuleIds.add(rule.getId());
                alertIds.add(alert.getId());
            }
        }
        return Optional.of(RuleEngineResult.of(
                transactionId,
                matchedRuleIds,
                alertIds
        ));
    }

    private CreateAlertCommand toAlertCommand(
            Rule rule,
            Transaction transaction,
            RuleEvaluationResult result
    ) {
        CreateAlertCommand command = new CreateAlertCommand();
        command.setRuleId(rule.getId());
        command.setTriggerTransactionId(transaction.getId());
        command.setAccountId(transaction.getAccountId());
        command.setRuleName(rule.getName());
        command.setSeverity(rule.getSeverity());
        command.setTitle(result.getTitle());
        command.setDescription(result.getDescription());
        command.setRelatedTransactionIds(result.getRelatedTransactionIds());
        return command;
    }
}
