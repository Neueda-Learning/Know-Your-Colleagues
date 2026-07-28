package com.example.knowyourcolleagues.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.knowyourcolleagues.bizexception.rule.ConcurrentRuleUpdateException;
import com.example.knowyourcolleagues.bizexception.rule.RuleDeletionConflictException;
import com.example.knowyourcolleagues.bizexception.rule.InvalidRuleRequestException;
import com.example.knowyourcolleagues.bizexception.rule.RuleNotFoundException;
import com.example.knowyourcolleagues.dto.CreateRuleRequest;
import com.example.knowyourcolleagues.dto.RulePageResponse;
import com.example.knowyourcolleagues.dto.RuleResponse;
import com.example.knowyourcolleagues.dto.UpdateRuleEnabledRequest;
import com.example.knowyourcolleagues.dto.UpdateRuleRequest;
import com.example.knowyourcolleagues.entity.Rule;
import com.example.knowyourcolleagues.entity.Alert;
import com.example.knowyourcolleagues.enums.RuleType;
import com.example.knowyourcolleagues.enums.Severity;
import com.example.knowyourcolleagues.mapper.RuleMapper;
import com.example.knowyourcolleagues.mapper.AlertMapper;
import com.example.knowyourcolleagues.service.RuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RuleServiceImpl implements RuleService {

    private static final long MAX_PAGE_SIZE = 100;

    private final RuleMapper ruleMapper;
    private final AlertMapper alertMapper;
    private final Clock clock = Clock.systemUTC();

    @Override
    @Transactional
    public RuleResponse createRule(CreateRuleRequest request) {
        validateCreateRequest(request);

        Instant now = clock.instant();
        Rule rule = new Rule();
        rule.setName(request.getName().trim());
        rule.setDescription(trimToNull(request.getDescription()));
        rule.setType(request.getType());
        rule.setSeverity(request.getSeverity());
        rule.setEnabled(request.getEnabled() == null
                ? Boolean.TRUE
                : request.getEnabled());
        applyParameters(
                rule,
                request.getCurrency(),
                request.getThresholdAmount(),
                request.getTransactionCount(),
                request.getTimeWindowMinutes(),
                request.getDailyLimitAmount()
        );
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        rule.setVersion(0);
        ruleMapper.insert(rule);
        return toResponse(rule);
    }

    @Override
    @Transactional(readOnly = true)
    public RulePageResponse getRules(
            String keyword,
            RuleType type,
            Boolean enabled,
            Severity severity,
            long page,
            long size
    ) {
        validatePage(page, size);
        Page<Rule> result = ruleMapper.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<Rule>()
                        .and(hasText(keyword), wrapper -> wrapper
                                .like(Rule::getName, keyword.trim())
                                .or()
                                .like(Rule::getDescription, keyword.trim()))
                        .eq(type != null, Rule::getType, type)
                        .eq(enabled != null, Rule::getEnabled, enabled)
                        .eq(severity != null, Rule::getSeverity, severity)
                        .orderByDesc(Rule::getCreatedAt)
                        .orderByDesc(Rule::getId)
        );

        RulePageResponse response = new RulePageResponse();
        response.setContent(result.getRecords().stream()
                .map(this::toResponse)
                .toList());
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(result.getTotal());
        response.setTotalPages(result.getPages());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public RuleResponse getRule(Long ruleId) {
        return toResponse(requireRule(ruleId));
    }

    @Override
    @Transactional
    public void deleteRule(Long ruleId) {
        requireRule(ruleId);
        Long alertCount = alertMapper.selectCount(
                new LambdaQueryWrapper<Alert>()
                        .eq(Alert::getRuleId, ruleId)
        );
        if (alertCount != null && alertCount > 0) {
            throw new RuleDeletionConflictException(
                    "Rule cannot be deleted because alerts reference it: "
                            + ruleId
            );
        }
        int deletedRows = ruleMapper.deleteById(ruleId);
        if (deletedRows != 1) {
            throw new RuleNotFoundException("Rule not found: " + ruleId);
        }
    }

    @Override
    @Transactional
    public RuleResponse updateRule(
            Long ruleId,
            UpdateRuleRequest request
    ) {
        if (request == null) {
            throw new InvalidRuleRequestException("rule request is required");
        }
        Rule rule = requireRule(ruleId);
        requireVersion(request.getVersion(), rule);
        if (!hasText(request.getName())) {
            throw new InvalidRuleRequestException("name is required");
        }
        if (request.getSeverity() == null) {
            throw new InvalidRuleRequestException("severity is required");
        }

        rule.setName(request.getName().trim());
        rule.setDescription(trimToNull(request.getDescription()));
        rule.setSeverity(request.getSeverity());
        applyParameters(
                rule,
                request.getCurrency(),
                request.getThresholdAmount(),
                request.getTransactionCount(),
                request.getTimeWindowMinutes(),
                request.getDailyLimitAmount()
        );
        validateRuleParameters(rule);
        rule.setUpdatedAt(clock.instant());
        updateWithOptimisticLock(rule);
        return toResponse(rule);
    }

    @Override
    @Transactional
    public RuleResponse updateEnabled(
            Long ruleId,
            UpdateRuleEnabledRequest request
    ) {
        if (request == null || request.getEnabled() == null) {
            throw new InvalidRuleRequestException("enabled is required");
        }
        Rule rule = requireRule(ruleId);
        requireVersion(request.getVersion(), rule);
        rule.setEnabled(request.getEnabled());
        rule.setUpdatedAt(clock.instant());
        updateWithOptimisticLock(rule);
        return toResponse(rule);
    }

    private Rule requireRule(Long ruleId) {
        if (ruleId == null || ruleId <= 0) {
            throw new InvalidRuleRequestException(
                    "ruleId must be positive"
            );
        }
        Rule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new RuleNotFoundException("Rule not found: " + ruleId);
        }
        return rule;
    }

    private void validateCreateRequest(CreateRuleRequest request) {
        if (request == null) {
            throw new InvalidRuleRequestException("rule request is required");
        }
        if (!hasText(request.getName())) {
            throw new InvalidRuleRequestException("name is required");
        }
        if (request.getType() == null) {
            throw new InvalidRuleRequestException("type is required");
        }
        if (request.getSeverity() == null) {
            throw new InvalidRuleRequestException("severity is required");
        }

        Rule validationRule = new Rule();
        validationRule.setType(request.getType());
        applyParameters(
                validationRule,
                request.getCurrency(),
                request.getThresholdAmount(),
                request.getTransactionCount(),
                request.getTimeWindowMinutes(),
                request.getDailyLimitAmount()
        );
        validateRuleParameters(validationRule);
    }

    private void validateRuleParameters(Rule rule) {
        if (rule.getCurrency() != null
                && !rule.getCurrency().matches("[A-Z]{3}")) {
            throw new InvalidRuleRequestException(
                    "currency must be a three-letter code"
            );
        }
        switch (rule.getType()) {
            case AMOUNT_THRESHOLD -> requirePositive(
                    rule.getThresholdAmount(),
                    "thresholdAmount"
            );
            case VELOCITY -> {
                requirePositive(rule.getTransactionCount(), "transactionCount");
                requirePositive(
                        rule.getTimeWindowMinutes(),
                        "timeWindowMinutes"
                );
            }
            case NEW_PAYEE -> {
                // NEW_PAYEE has no additional numeric parameter.
            }
            case DAILY_LIMIT -> requirePositive(
                    rule.getDailyLimitAmount(),
                    "dailyLimitAmount"
            );
        }
    }

    private void applyParameters(
            Rule rule,
            String currency,
            java.math.BigDecimal thresholdAmount,
            Integer transactionCount,
            Integer timeWindowMinutes,
            java.math.BigDecimal dailyLimitAmount
    ) {
        rule.setCurrency(hasText(currency)
                ? currency.trim().toUpperCase(Locale.ROOT)
                : null);
        rule.setThresholdAmount(thresholdAmount);
        rule.setTransactionCount(transactionCount);
        rule.setTimeWindowMinutes(timeWindowMinutes);
        rule.setDailyLimitAmount(dailyLimitAmount);
    }

    private void requirePositive(Number value, String field) {
        if (value == null || value.doubleValue() <= 0) {
            throw new InvalidRuleRequestException(
                    field + " must be greater than 0"
            );
        }
    }

    private void validatePage(long page, long size) {
        if (page < 0) {
            throw new InvalidRuleRequestException(
                    "page must not be negative"
            );
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new InvalidRuleRequestException(
                    "size must be between 1 and " + MAX_PAGE_SIZE
            );
        }
    }

    private void requireVersion(Integer requestedVersion, Rule rule) {
        if (requestedVersion == null) {
            throw new InvalidRuleRequestException("version is required");
        }
        if (!requestedVersion.equals(rule.getVersion())) {
            throw concurrentUpdate(rule.getId());
        }
    }

    private void updateWithOptimisticLock(Rule rule) {
        if (ruleMapper.updateById(rule) != 1) {
            throw concurrentUpdate(rule.getId());
        }
    }

    private ConcurrentRuleUpdateException concurrentUpdate(Long ruleId) {
        return new ConcurrentRuleUpdateException(
                "Rule was updated concurrently: " + ruleId
        );
    }

    private RuleResponse toResponse(Rule rule) {
        RuleResponse response = new RuleResponse();
        response.setId(rule.getId());
        response.setName(rule.getName());
        response.setDescription(rule.getDescription());
        response.setType(rule.getType());
        response.setSeverity(rule.getSeverity());
        response.setEnabled(rule.getEnabled());
        response.setCurrency(rule.getCurrency());
        response.setThresholdAmount(rule.getThresholdAmount());
        response.setTransactionCount(rule.getTransactionCount());
        response.setTimeWindowMinutes(rule.getTimeWindowMinutes());
        response.setDailyLimitAmount(rule.getDailyLimitAmount());
        response.setCreatedAt(rule.getCreatedAt());
        response.setUpdatedAt(rule.getUpdatedAt());
        response.setVersion(rule.getVersion());
        return response;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
