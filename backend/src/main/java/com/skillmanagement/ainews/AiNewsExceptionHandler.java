package com.skillmanagement.ainews;

import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.exception.BusinessErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception handler scoped to the AI News module (keeps the NEWS_NOT_FOUND
 * error code contract of AI_News_Screen_SRS.md §5.4 without touching the
 * shared GlobalExceptionHandler).
 */
@RestControllerAdvice(basePackages = "com.skillmanagement.ainews")
public class AiNewsExceptionHandler {

    @ExceptionHandler(NewsNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNewsNotFound(NewsNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(BusinessErrorCode.NEWS_NOT_FOUND.name(), ex.getMessage()));
    }
}
