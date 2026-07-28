package com.example.knowyourcolleagues.bizexception.rule;

public class RuleDeletionConflictException extends RuntimeException {

    public RuleDeletionConflictException(String message) {
        super(message);
    }
}
