package com.example.knowyourcolleagues.bizexception.alert;

public class InvalidAlertRequestException extends RuntimeException {

    public InvalidAlertRequestException(String message) {
        super(message);
    }
}
