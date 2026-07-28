package com.example.knowyourcolleagues.service;

import com.example.knowyourcolleagues.dto.RuleEngineResult;

import java.util.Optional;

public interface RuleEngineService {

    /**
     * 对等待校验的交易执行全部启用规则。
     *
     * @return 规则汇总结果；交易已经完成校验时返回空，避免重复发布结果消息
     */
    Optional<RuleEngineResult> evaluateTransaction(Long transactionId);
}
