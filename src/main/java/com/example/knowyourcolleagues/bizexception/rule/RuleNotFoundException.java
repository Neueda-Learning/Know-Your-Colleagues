package com.example.knowyourcolleagues.bizexception.rule;

public class RuleNotFoundException extends RuntimeException {

    public RuleNotFoundException(String message) {
        super(message);
    }
}
