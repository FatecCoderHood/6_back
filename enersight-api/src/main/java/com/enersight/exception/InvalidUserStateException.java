package com.enersight.exception;

public class InvalidUserStateException extends RuntimeException {
    public InvalidUserStateException(String message) {
        super(message);
    }
}
