package com.example.knowyourcolleagues.service;

import com.example.knowyourcolleagues.dto.RuleEngineResult;

public interface RuleEngineService {

    RuleEngineResult evaluateTransaction(Long transactionId);
}
