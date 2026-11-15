package com.furelise.skillmanagement.exception;

import org.springframework.http.HttpStatus;

/**
 * Business rule violation carrying a stable error code (SRS error catalogs).
 */
public class BusinessException extends RuntimeException {

    private final BusinessErrorCode code;
    /** Optional structured payload (e.g. MCP blockers[]) carried in the
     *  error response's {@code data} field (FR-MCP-016, v1.33). */
    private final Object data;

    public BusinessException(BusinessErrorCode code) {
        super(code.name());
        this.code = code;
        this.data = null;
    }

    public BusinessException(BusinessErrorCode code, String message) {
        super(message);
        this.code = code;
        this.data = null;
    }

    public BusinessException(BusinessErrorCode code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public BusinessErrorCode getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }

    public HttpStatus status() {
        return code.status();
    }
}
