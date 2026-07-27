package com.example.knowyourcolleagues.bizexception.transaction;

/**
 * 根据指定条件找不到交易时抛出的异常。
 */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String message) {
        super(message);
    }
}
