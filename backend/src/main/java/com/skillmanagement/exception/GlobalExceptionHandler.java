package com.skillmanagement.exception;

import com.skillmanagement.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global REST exception handler.
 * All error responses use the standard envelope:
 * { "success": false, "errorCode": "...", "message": "..." }
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("errorCode", "VALIDATION_FAILED");
        body.put("message", "Input data is invalid.");
        body.put("details", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.status())
                .body(ApiResponse.error(ex.getCode().name(), ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        // Distinguish a duplicate-email race (user create/register) from other
        // integrity violations (e.g. org import composite-PK race).
        if (isDuplicateEmail(ex)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("EMAIL_ALREADY_USED", "Email is already in use."));
        }
        // Log the real constraint so violations are diagnosable (the generic
        // message below does not name the offending constraint).
        log.warn("Data integrity violation: {}", rootMessage(ex));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("DATA_INTEGRITY_CONFLICT",
                        "This record already exists in the database. Please reload and try again."));
    }

    private String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    /** True when the underlying SQL error is a violation of the user email unique constraint. */
    private boolean isDuplicateEmail(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        return msg != null && msg.toLowerCase().contains("uk_user_email_ci");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", "You do not have permission to access this resource."));
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenRefresh(TokenRefreshException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("TOKEN_INVALID", ex.getMessage()));
    }

    @ExceptionHandler(TokenReuseException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenReuse(TokenReuseException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("TOKEN_REUSED", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        // Technical details are never exposed (SRS security rule) — but log
        // the full stack trace server-side so the real cause is diagnosable.
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "System error. Please try again later."));
    }
}
