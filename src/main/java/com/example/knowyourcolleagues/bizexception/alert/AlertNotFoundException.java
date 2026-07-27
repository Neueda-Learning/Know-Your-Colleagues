package com.example.knowyourcolleagues.bizexception.alert;

public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(String message) {
        super(message);
    }
}
