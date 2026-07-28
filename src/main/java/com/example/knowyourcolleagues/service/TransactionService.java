package com.example.knowyourcolleagues.service;

import com.example.knowyourcolleagues.dto.CreateTransactionRequest;
import com.example.knowyourcolleagues.dto.TransactionPageResponse;
import com.example.knowyourcolleagues.dto.TransactionQueryRequest;
import com.example.knowyourcolleagues.dto.TransactionResponse;
import com.example.knowyourcolleagues.enums.TransactionStatus;

/**
 * 交易业务服务。
 */
public interface TransactionService {

    TransactionResponse createTransaction(CreateTransactionRequest request);

    TransactionPageResponse getTransactions(TransactionQueryRequest query);

    TransactionResponse getTransaction(Long transactionId);

    /**
     * 根据规则评估结果，将等待校验的交易更新为最终状态。
     */
    void updateStatusAfterEvaluation(
            Long transactionId,
            TransactionStatus targetStatus
    );
}
