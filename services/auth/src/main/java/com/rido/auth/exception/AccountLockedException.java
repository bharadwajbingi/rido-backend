package com.rido.auth.exception;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String msg) {
        super(msg);
    }
}
