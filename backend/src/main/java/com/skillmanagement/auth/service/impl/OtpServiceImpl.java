package com.skillmanagement.auth.service.impl;

import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.auth.model.OtpManagement;
import com.skillmanagement.auth.repository.OtpRepository;
import com.skillmanagement.mail.EmailService;
import com.skillmanagement.auth.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * OTP service (Login SRS):
 * - 6-digit code, valid 5 minutes (BR-08)
 * - only the latest OTP per (type, email) is valid (BR-09)
 * - verified OTPs cannot be reused (BR-10)
 * - stored as SHA-256 hash (BR-07)
 * - resend invalidates the previous OTP (BR-09)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final int OTP_TTL_SECONDS = 300; // 5 minutes

    private final OtpRepository otpRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public String requestOtp(String email, String otpType, Long userId, String clientIp) {
        // Invalidate previous ACTIVE OTPs (latest-only rule, resend rule)
        otpRepository.deleteByEmailIgnoreCaseAndOtpTypeAndStatus(email, otpType, "ACTIVE");

        String otp = String.valueOf(100000 + secureRandom.nextInt(900000));

        OtpManagement record = OtpManagement.builder()
                .userId(userId)
                .email(email)
                .otpHash(sha256(otp))
                .otpType(otpType)
                .expiredAt(Instant.now().plusSeconds(OTP_TTL_SECONDS))
                .status("ACTIVE")
                .createdIp(clientIp)
                .build();
        otpRepository.save(record);

        String subject = "EMAIL_VERIFICATION".equals(otpType)
                ? "Your registration verification code"
                : "Your password reset code";
        emailService.sendOtpEmail(email, otpType, otp, subject);
        return otp;
    }

    @Override
    @Transactional
    public void verifyOtp(String email, String otpType, String plainOtp) {
        OtpManagement otp = otpRepository
                .findFirstByEmailIgnoreCaseAndOtpTypeOrderByOtpIdDesc(email, otpType)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.OTP_INVALID, "The code is incorrect."));

        if ("VERIFIED".equals(otp.getStatus())) {
            // A verified OTP cannot be reused (BR-10)
            throw new BusinessException(BusinessErrorCode.OTP_INVALID, "This code has already been used.");
        }
        if ("EXPIRED".equals(otp.getStatus())
                || otp.getExpiredAt().isBefore(Instant.now())) {
            otp.setStatus("EXPIRED");
            otpRepository.save(otp);
            throw new BusinessException(BusinessErrorCode.OTP_EXPIRED, "The code has expired. Please request a new one.");
        }
        if (!otp.getOtpHash().equals(sha256(plainOtp))) {
            throw new BusinessException(BusinessErrorCode.OTP_INVALID, "The code is incorrect.");
        }

        otp.setStatus("VERIFIED");
        otp.setVerifiedAt(Instant.now());
        otpRepository.save(otp);
    }

    @Override
    @Transactional
    public void consumeVerifiedOtp(String email, String otpType) {
        OtpManagement otp = otpRepository
                .findFirstByEmailIgnoreCaseAndOtpTypeOrderByOtpIdDesc(email, otpType)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.OTP_NOT_VERIFIED,
                        "Please verify the code before continuing."));
        if (!"VERIFIED".equals(otp.getStatus())) {
            throw new BusinessException(BusinessErrorCode.OTP_NOT_VERIFIED,
                    "Please verify the code before continuing.");
        }
        // One-shot: the verified OTP cannot be consumed twice
        otpRepository.delete(otp);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
