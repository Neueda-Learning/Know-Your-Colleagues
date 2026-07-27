package com.example.knowyourcolleagues.bizexception.transaction;

/**
 * 交易请求中的业务参数不合法时抛出的异常。
 */
public class InvalidTransactionRequestException extends RuntimeException {

    public InvalidTransactionRequestException(String message) {
        super(message);
    }
}
