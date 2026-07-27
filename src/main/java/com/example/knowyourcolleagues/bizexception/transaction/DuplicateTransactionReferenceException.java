package com.example.knowyourcolleagues.bizexception.transaction;

/**
 * 创建交易时使用了已经存在的业务参考号。
 */
public class DuplicateTransactionReferenceException extends RuntimeException {

    public DuplicateTransactionReferenceException(String message) {
        super(message);
    }
}
