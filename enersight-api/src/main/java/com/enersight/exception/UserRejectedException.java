package com.enersight.exception;

public class UserRejectedException extends RuntimeException {
    public UserRejectedException() {
        super("User access request was rejected");
    }
}
