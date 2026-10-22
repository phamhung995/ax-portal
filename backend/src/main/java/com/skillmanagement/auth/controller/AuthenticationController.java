package com.skillmanagement.auth.controller;

import com.skillmanagement.auth.dto.*;
import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.user.dto.*;
import com.skillmanagement.auth.dto.*;
import com.skillmanagement.skill.dto.*;
import com.skillmanagement.auth.service.AuthenticationService;
import com.skillmanagement.auth.service.BlacklistService;
import com.skillmanagement.auth.service.RefreshTokenService;
import com.skillmanagement.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints (Login SRS v1.6):
 * login, 3-step registration with OTP, forgot/reset password with OTP,
 * refresh token and logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;
    private final BlacklistService blacklistService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(ApiResponse.ok(
                authenticationService.login(request, clientIp(http))));
    }

    // ---------------- Registration (3 steps) ----------------

    @PostMapping("/register/request-otp")
    public ResponseEntity<ApiResponse<Void>> requestRegistrationOtp(
            @Valid @RequestBody OtpRequest request, HttpServletRequest http) {
        authenticationService.requestRegistrationOtp(request.email(), clientIp(http));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/register/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verifyRegistrationOtp(
            @Valid @RequestBody OtpVerifyRequest request) {
        authenticationService.verifyRegistrationOtp(request.email(), request.otp());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        authenticationService.register(request, clientIp(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    // ---------------- Forgot / reset password ----------------

    @PostMapping("/forgot-password/request-otp")
    public ResponseEntity<ApiResponse<Void>> requestPasswordResetOtp(
            @Valid @RequestBody OtpRequest request, HttpServletRequest http) {
        authenticationService.requestPasswordResetOtp(request.email(), clientIp(http));
        // Anti-enumeration: always the same success response
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verifyPasswordResetOtp(
            @Valid @RequestBody OtpVerifyRequest request) {
        authenticationService.verifyPasswordResetOtp(request.email(), request.otp());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        authenticationService.resetPassword(request, clientIp(http));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ---------------- Session ----------------

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                refreshTokenService.rotateRefreshToken(request.refreshToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) LogoutRequest request) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            blacklistService.blacklistToken(authHeader.substring(7));
        }
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshTokenService.revokeToken(request.refreshToken());
        }
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal User currentUser) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            blacklistService.blacklistToken(authHeader.substring(7));
        }
        if (currentUser != null) {
            refreshTokenService.revokeAllUserTokens(currentUser.getId());
        }
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
