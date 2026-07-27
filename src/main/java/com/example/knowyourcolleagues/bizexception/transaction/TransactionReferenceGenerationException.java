package com.example.knowyourcolleagues.bizexception.transaction;

/**
 * 后端多次尝试后仍无法生成唯一交易号时抛出的异常。
 */
public class TransactionReferenceGenerationException extends RuntimeException {

    public TransactionReferenceGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
