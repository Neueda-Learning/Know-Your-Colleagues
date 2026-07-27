package com.example.knowyourcolleagues.common.exception;

import com.example.knowyourcolleagues.bizexception.alert.AlertNotFoundException;
import com.example.knowyourcolleagues.bizexception.alert.ConcurrentAlertUpdateException;
import com.example.knowyourcolleagues.bizexception.alert.InvalidAlertRequestException;
import com.example.knowyourcolleagues.bizexception.alert.InvalidAlertTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AlertNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAlertNotFound(
            AlertNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "ALERT_NOT_FOUND",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidAlertTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(
            InvalidAlertTransitionException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "INVALID_ALERT_TRANSITION",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(ConcurrentAlertUpdateException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(
            ConcurrentAlertUpdateException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "ALERT_CONCURRENT_UPDATE",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidAlertRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(
            InvalidAlertRequestException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_ALERT_REQUEST",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request parameters or body are invalid",
                request
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        ErrorResponse body = new ErrorResponse(
                code,
                message,
                Instant.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
