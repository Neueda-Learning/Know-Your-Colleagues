package com.example.knowyourcolleagues.bizexception.demo;

public class DemoScenarioUnavailableException extends RuntimeException {

    public DemoScenarioUnavailableException(String message) {
        super(message);
    }

    public DemoScenarioUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
