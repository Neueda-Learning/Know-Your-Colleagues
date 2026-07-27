package com.example.knowyourcolleagues.service;

import com.example.knowyourcolleagues.dto.CreateRuleRequest;
import com.example.knowyourcolleagues.dto.RulePageResponse;
import com.example.knowyourcolleagues.dto.RuleResponse;
import com.example.knowyourcolleagues.dto.UpdateRuleEnabledRequest;
import com.example.knowyourcolleagues.dto.UpdateRuleRequest;
import com.example.knowyourcolleagues.enums.RuleType;
import com.example.knowyourcolleagues.enums.Severity;

public interface RuleService {

    RuleResponse createRule(CreateRuleRequest request);

    RulePageResponse getRules(
            RuleType type,
            Boolean enabled,
            Severity severity,
            long page,
            long size
    );

    RuleResponse getRule(Long ruleId);

    RuleResponse updateRule(Long ruleId, UpdateRuleRequest request);

    RuleResponse updateEnabled(
            Long ruleId,
            UpdateRuleEnabledRequest request
    );
}
