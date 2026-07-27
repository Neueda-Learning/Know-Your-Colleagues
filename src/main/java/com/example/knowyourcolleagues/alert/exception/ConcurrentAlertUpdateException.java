package com.example.knowyourcolleagues.alert.exception;

public class ConcurrentAlertUpdateException extends RuntimeException {

    public ConcurrentAlertUpdateException(String message) {
        super(message);
    }
}
