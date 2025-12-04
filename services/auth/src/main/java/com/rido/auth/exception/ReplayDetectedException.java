package com.rido.auth.exception;

public class ReplayDetectedException extends RuntimeException {
    public ReplayDetectedException(String message) {
        super(message);
    }
}
