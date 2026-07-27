package com.example.knowyourcolleagues.bizexception.alert;

public class InvalidAlertTransitionException extends RuntimeException {

    public InvalidAlertTransitionException(String message) {
        super(message);
    }
}
