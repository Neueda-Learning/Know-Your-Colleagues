package com.example.knowyourcolleagues.service;

import com.example.knowyourcolleagues.dto.CreateTransactionRequest;
import com.example.knowyourcolleagues.dto.TransactionPageResponse;
import com.example.knowyourcolleagues.dto.TransactionQueryRequest;
import com.example.knowyourcolleagues.dto.TransactionResponse;

/**
 * 交易业务服务。
 */
public interface TransactionService {

    TransactionResponse createTransaction(CreateTransactionRequest request);

    TransactionPageResponse getTransactions(TransactionQueryRequest query);

    TransactionResponse getTransaction(Long transactionId);
}
