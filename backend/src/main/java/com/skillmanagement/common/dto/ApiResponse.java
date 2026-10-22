package com.skillmanagement.common.dto;

/**
 * Standard API envelope (SRS §7):
 * success: { "success": true, "data": ... }
 * error:   { "success": false, "errorCode": "...", "message": "..." }
 */
public final class ApiResponse<T> {

    private boolean success;
    private T data;
    private String errorCode;
    private String message;

    private ApiResponse() {
    }

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.errorCode = errorCode;
        r.message = message;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
