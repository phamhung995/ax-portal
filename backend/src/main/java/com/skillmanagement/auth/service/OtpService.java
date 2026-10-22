package com.skillmanagement.auth.service;

/**
 * OTP lifecycle (Login SRS): 6 digits, 5 minutes, latest-only per (type, email),
 * verified OTPs cannot be reused. Only hashes are stored.
 */
public interface OtpService {

    String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    String PASSWORD_RESET = "PASSWORD_RESET";

    /**
     * Generates a new OTP for (type, email), invalidates previous ACTIVE OTPs,
     * persists the hash and sends the email via EmailService.
     *
     * @return the plain OTP (used only for the dev log; never persisted)
     */
    String requestOtp(String email, String otpType, Long userId, String clientIp);

    /**
     * Verifies the plain OTP against the latest ACTIVE OTP for (type, email).
     * On success marks it VERIFIED (one-shot).
     */
    void verifyOtp(String email, String otpType, String plainOtp);

    /**
     * Step-3 flows (register / reset password): requires that a VERIFIED OTP
     * exists for (type, email), then deletes it (one-shot consumption).
     */
    void consumeVerifiedOtp(String email, String otpType);
}
