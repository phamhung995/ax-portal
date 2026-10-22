package com.skillmanagement.mail.impl;

import com.skillmanagement.model.EmailManagement;
import com.skillmanagement.repository.EmailRepository;
import com.skillmanagement.mail.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Dev OTP email: logs the OTP to the console and appends it to backend/otp-dev.log
 * instead of sending mail. Active when the Knox Mail API is not configured.
 */
@Service
@Slf4j
@ConditionalOnExpression("'${knox.mail.system-id:}'.isEmpty()")
public class LogEmailServiceImpl implements EmailService {

    private static final Path OTP_LOG = Path.of("otp-dev.log");

    private final EmailRepository emailRepository;

    public LogEmailServiceImpl(EmailRepository emailRepository) {
        this.emailRepository = emailRepository;
    }

    @Override
    public void sendOtpEmail(String to, String otpType, String otp, String subject) {
        log.info("====================================================================");
        log.info("[DEV OTP EMAIL] To: {} | Type: {} | Subject: {}", to, otpType, subject);
        log.info("[DEV OTP EMAIL] OTP: {}", otp);
        log.info("====================================================================");

        appendOtpLog(to, otpType, otp);

        emailRepository.save(EmailManagement.builder()
                .email(to)
                .emailType(otpType)
                .subject(subject)
                .status("SENT")
                .sentAt(Instant.now())
                .build());
    }

    @Override
    public void sendNotificationEmail(String to, String subject, String body) {
        log.info("[DEV NOTIFICATION EMAIL] To: {} | Subject: {}", to, subject);
        emailRepository.save(EmailManagement.builder()
                .email(to)
                .emailType("SKILL_NOTIFICATION")
                .subject(subject)
                .status("SENT")
                .sentAt(Instant.now())
                .build());
    }

    private void appendOtpLog(String to, String otpType, String otp) {
        try {
            Path parent = OTP_LOG.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(OTP_LOG,
                    String.format("[%s] To: %s | Type: %s | OTP: %s%n", Instant.now(), to, otpType, otp),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("Could not append OTP to {}: {}", OTP_LOG, e.getMessage());
        }
    }
}
