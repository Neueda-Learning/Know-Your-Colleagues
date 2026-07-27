package com.example.knowyourcolleagues.bizexception.rule;

public class UnsupportedRuleTypeException extends RuntimeException {

    public UnsupportedRuleTypeException(String message) {
        super(message);
    }
}
