package com.skillmanagement.exception;

public class TokenReuseException extends RuntimeException {
    public TokenReuseException(String message) {
        super(message);
    }
}
