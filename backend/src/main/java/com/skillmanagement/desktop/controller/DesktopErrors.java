package com.skillmanagement.desktop.controller;

import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.desktop.service.DesktopFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages="com.skillmanagement.desktop.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DesktopErrors {
    @ExceptionHandler(DesktopFailure.class) public ResponseEntity<ApiResponse<Void>> failure(DesktopFailure failure) {
        return ResponseEntity.status(failure.status()).body(ApiResponse.error(failure.code(),failure.getMessage()));
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class) public ResponseEntity<ApiResponse<Void>> invalidQuery() { return failure(DesktopFailure.query()); }
}
