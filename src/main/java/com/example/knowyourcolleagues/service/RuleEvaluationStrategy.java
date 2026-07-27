package com.example.knowyourcolleagues.service;

import com.example.knowyourcolleagues.dto.RuleEvaluationResult;
import com.example.knowyourcolleagues.entity.Rule;
import com.example.knowyourcolleagues.entity.Transaction;
import com.example.knowyourcolleagues.enums.RuleType;

public interface RuleEvaluationStrategy {

    RuleType supportedType();

    RuleEvaluationResult evaluate(Rule rule, Transaction transaction);
}
