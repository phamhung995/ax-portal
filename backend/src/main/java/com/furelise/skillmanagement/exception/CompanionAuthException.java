package com.furelise.skillmanagement.exception;

import org.springframework.http.HttpStatus;

/**
 * Companion-app authentication failure (GĐ6, BA Q5): missing/invalid token
 * (401), bad HMAC signature (403), rate limit exceeded (429), or the
 * companion token not configured on the server at all (503, fail closed).
 * Mapped to the standard error envelope by GlobalExceptionHandler.
 */
public class CompanionAuthException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public CompanionAuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
