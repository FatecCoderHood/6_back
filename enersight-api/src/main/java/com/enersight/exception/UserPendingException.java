package com.enersight.exception;

public class UserPendingException extends RuntimeException {
    public UserPendingException(String message) {
        super(message);
    }
}
