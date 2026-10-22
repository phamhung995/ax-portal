package com.skillmanagement.exception;

import org.springframework.http.HttpStatus;

/**
 * Business rule violation carrying a stable error code (SRS error catalogs).
 */
public class BusinessException extends RuntimeException {

    private final BusinessErrorCode code;

    public BusinessException(BusinessErrorCode code) {
        super(code.name());
        this.code = code;
    }

    public BusinessException(BusinessErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessErrorCode getCode() {
        return code;
    }

    public HttpStatus status() {
        return code.status();
    }
}
