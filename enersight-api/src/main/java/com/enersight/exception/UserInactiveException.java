package com.enersight.exception;

public class UserInactiveException extends RuntimeException {
    public UserInactiveException() {
        super("User account is deactivated");
    }
}
