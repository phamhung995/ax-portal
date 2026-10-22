package com.skillmanagement.mail;

/**
 * Sends OTP emails (SRS BR-11/12: frontend never calls the email provider;
 * all OTP mail goes through this internal service).
 */
public interface EmailService {

    void sendOtpEmail(String to, String otpType, String otp, String subject);

    /** Sends a plain-text notification email (skill workflow / new-version). */
    void sendNotificationEmail(String to, String subject, String body);
}
