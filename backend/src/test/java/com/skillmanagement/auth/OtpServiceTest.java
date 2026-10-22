package com.skillmanagement.auth;

import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.auth.model.OtpManagement;
import com.skillmanagement.auth.repository.OtpRepository;
import com.skillmanagement.auth.service.OtpService;
import com.skillmanagement.auth.service.impl.OtpServiceImpl;
import com.skillmanagement.mail.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpRepository otpRepository;
    @Mock
    private EmailService emailService;
    @InjectMocks
    private OtpServiceImpl otpService;

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void requestOtp_invalidatesPreviousActiveAndSendsEmail() {
        when(otpRepository.save(any(OtpManagement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String otp = otpService.requestOtp("user@example.com", OtpService.EMAIL_VERIFICATION, null, "1.2.3.4");

        assertEquals(6, otp.length());
        verify(otpRepository).deleteByEmailIgnoreCaseAndOtpTypeAndStatus(
                "user@example.com", OtpService.EMAIL_VERIFICATION, "ACTIVE");
        verify(emailService).sendOtpEmail(eq("user@example.com"), eq(OtpService.EMAIL_VERIFICATION),
                eq(otp), anyString());
    }

    @Test
    void verifyOtp_success_marksVerified() {
        OtpManagement otp = OtpManagement.builder()
                .email("user@example.com")
                .otpHash(sha256("123456"))
                .status("ACTIVE")
                .expiredAt(Instant.now().plusSeconds(300))
                .build();
        when(otpRepository.findFirstByEmailIgnoreCaseAndOtpTypeOrderByOtpIdDesc(
                "user@example.com", OtpService.EMAIL_VERIFICATION)).thenReturn(Optional.of(otp));
        when(otpRepository.save(any(OtpManagement.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> otpService.verifyOtp("user@example.com", OtpService.EMAIL_VERIFICATION, "123456"));
        assertEquals("VERIFIED", otp.getStatus());
        assertNotNull(otp.getVerifiedAt());
    }

    @Test
    void verifyOtp_wrongCode_throwsOtpInvalid() {
        OtpManagement otp = OtpManagement.builder()
                .email("user@example.com")
                .otpHash(sha256("123456"))
                .status("ACTIVE")
                .expiredAt(Instant.now().plusSeconds(300))
                .build();
        when(otpRepository.findFirstByEmailIgnoreCaseAndOtpTypeOrderByOtpIdDesc(
                "user@example.com", OtpService.EMAIL_VERIFICATION)).thenReturn(Optional.of(otp));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> otpService.verifyOtp("user@example.com", OtpService.EMAIL_VERIFICATION, "654321"));
        assertEquals(BusinessErrorCode.OTP_INVALID, ex.getCode());
    }

    @Test
    void verifyOtp_expired_throwsOtpExpired() {
        OtpManagement otp = OtpManagement.builder()
                .email("user@example.com")
                .otpHash(sha256("123456"))
                .status("ACTIVE")
                .expiredAt(Instant.now().minusSeconds(1))
                .build();
        when(otpRepository.findFirstByEmailIgnoreCaseAndOtpTypeOrderByOtpIdDesc(
                "user@example.com", OtpService.EMAIL_VERIFICATION)).thenReturn(Optional.of(otp));
        when(otpRepository.save(any(OtpManagement.class))).thenAnswer(inv -> inv.getArgument(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> otpService.verifyOtp("user@example.com", OtpService.EMAIL_VERIFICATION, "123456"));
        assertEquals(BusinessErrorCode.OTP_EXPIRED, ex.getCode());
    }

    @Test
    void verifyOtp_alreadyVerified_cannotReuse() {
        OtpManagement otp = OtpManagement.builder()
                .email("user@example.com")
                .otpHash(sha256("123456"))
                .status("VERIFIED")
                .expiredAt(Instant.now().plusSeconds(300))
                .build();
        when(otpRepository.findFirstByEmailIgnoreCaseAndOtpTypeOrderByOtpIdDesc(
                "user@example.com", OtpService.EMAIL_VERIFICATION)).thenReturn(Optional.of(otp));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> otpService.verifyOtp("user@example.com", OtpService.EMAIL_VERIFICATION, "123456"));
        assertEquals(BusinessErrorCode.OTP_INVALID, ex.getCode());
    }

    @Test
    void consumeVerifiedOtp_requiresVerifiedAndDeletes() {
        OtpManagement otp = OtpManagement.builder()
                .email("user@example.com")
                .status("VERIFIED")
                .build();
        when(otpRepository.findFirstByEmailIgnoreCaseAndOtpTypeOrderByOtpIdDesc(
                "user@example.com", OtpService.EMAIL_VERIFICATION)).thenReturn(Optional.of(otp));

        assertDoesNotThrow(() -> otpService.consumeVerifiedOtp("user@example.com", OtpService.EMAIL_VERIFICATION));
        verify(otpRepository).delete(otp);
    }

    @Test
    void consumeVerifiedOtp_notVerified_throws() {
        OtpManagement otp = OtpManagement.builder()
                .email("user@example.com")
                .status("ACTIVE")
                .build();
        when(otpRepository.findFirstByEmailIgnoreCaseAndOtpTypeOrderByOtpIdDesc(
                "user@example.com", OtpService.EMAIL_VERIFICATION)).thenReturn(Optional.of(otp));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> otpService.consumeVerifiedOtp("user@example.com", OtpService.EMAIL_VERIFICATION));
        assertEquals(BusinessErrorCode.OTP_NOT_VERIFIED, ex.getCode());
    }
}
