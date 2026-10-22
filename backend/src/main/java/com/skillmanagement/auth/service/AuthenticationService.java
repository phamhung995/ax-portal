package com.skillmanagement.auth.service;

import com.skillmanagement.auth.dto.LoginRequest;
import com.skillmanagement.auth.dto.LoginResponse;
import com.skillmanagement.auth.dto.RegisterRequest;
import com.skillmanagement.auth.dto.ResetPasswordRequest;

/**
 * Authentication flows (Login SRS v1.6):
 * login, 3-step self-registration with OTP, forgot/reset password with OTP.
 */
public interface AuthenticationService {

    /**
     * Login sequence (SRS §8): email format -> user exists -> password ->
     * status DEACTIVE -> part/group populated. Logs every attempt.
     */
    LoginResponse login(LoginRequest request, String clientIp);

    /** Step 1: request EMAIL_VERIFICATION OTP (email must not exist). */
    void requestRegistrationOtp(String email, String clientIp);

    /** Step 2: verify the EMAIL_VERIFICATION OTP. */
    void verifyRegistrationOtp(String email, String otp);

    /** Step 3: create the account (requires verified OTP). */
    void register(RegisterRequest request, String clientIp);

    /** Request PASSWORD_RESET OTP. Anti-enumeration: always "success" message. */
    void requestPasswordResetOtp(String email, String clientIp);

    /** Verify the PASSWORD_RESET OTP. */
    void verifyPasswordResetOtp(String email, String otp);

    /**
     * Reset password (requires verified OTP). DEACTIVE -> ACTIVE is committed
     * in the same transaction as the password update (BR-16/19/20).
     */
    void resetPassword(ResetPasswordRequest request, String clientIp);
}
