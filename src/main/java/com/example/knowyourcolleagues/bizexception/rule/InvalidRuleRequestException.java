package com.example.knowyourcolleagues.bizexception.rule;

public class InvalidRuleRequestException extends RuntimeException {

    public InvalidRuleRequestException(String message) {
        super(message);
    }
}
