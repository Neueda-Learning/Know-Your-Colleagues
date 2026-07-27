package com.example.knowyourcolleagues.bizexception.rule;

public class ConcurrentRuleUpdateException extends RuntimeException {

    public ConcurrentRuleUpdateException(String message) {
        super(message);
    }
}
