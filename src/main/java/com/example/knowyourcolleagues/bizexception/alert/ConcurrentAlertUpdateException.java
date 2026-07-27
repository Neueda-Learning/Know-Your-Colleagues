package com.example.knowyourcolleagues.bizexception.alert;

public class ConcurrentAlertUpdateException extends RuntimeException {

    public ConcurrentAlertUpdateException(String message) {
        super(message);
    }
}
