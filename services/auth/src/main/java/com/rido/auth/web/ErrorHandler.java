package com.rido.auth.web;

import com.rido.auth.exception.AccountLockedException;
import com.rido.auth.exception.InvalidCredentialsException;
import com.rido.auth.exception.UsernameAlreadyExistsException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.Map;

@ControllerAdvice
public class ErrorHandler {

    private ResponseEntity<Object> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(
                Map.of(
                        "error", code,
                        "message", message,
                        "timestamp", Instant.now().toString()
                )
        );
    }

    // -----------------------------
    // Authentication / Login Errors
    // -----------------------------

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Object> handleInvalidCredentials(InvalidCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "invalid_credentials", ex.getMessage());
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Object> handleAccountLocked(AccountLockedException ex) {
        return build(HttpStatus.LOCKED, "account_locked", ex.getMessage());
    }

    // -----------------------------
    // Registration
    // -----------------------------

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<Object> handleUserExists(UsernameAlreadyExistsException ex) {
        return build(HttpStatus.CONFLICT, "username_exists", ex.getMessage());
    }

    // -----------------------------
    // Fallback (unexpected errors)
    // -----------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleOther(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", ex.getMessage());
    }
}
