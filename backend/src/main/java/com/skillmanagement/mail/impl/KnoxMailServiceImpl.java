package com.skillmanagement.mail.impl;

import com.skillmanagement.mail.KnoxMailClient;
import com.skillmanagement.model.EmailManagement;
import com.skillmanagement.repository.EmailRepository;
import com.skillmanagement.mail.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Knox C&C Suite Mail API email. Active when knox.mail.system-id is set
 * (linkage-issued Account ID); otherwise LogEmailServiceImpl (dev mode) is used.
 *
 * <p>Sender is the linkage-registered Knox account (knox.mail.sender-email /
 * knox.mail.sender-knox-id); recipients are the end users.</p>
 */
@Service
@Slf4j
@ConditionalOnExpression("!'${knox.mail.system-id:}'.isEmpty()")
public class KnoxMailServiceImpl implements EmailService {

    private final KnoxMailClient mailClient;
    private final EmailRepository emailRepository;
    private final String senderEmail;
    private final String senderKnoxId;

    public KnoxMailServiceImpl(
            KnoxMailClient mailClient,
            EmailRepository emailRepository,
            @Value("${knox.mail.sender-email:}") String senderEmail,
            @Value("${knox.mail.sender-knox-id:}") String senderKnoxId
    ) {
        this.mailClient = mailClient;
        this.emailRepository = emailRepository;
        this.senderEmail = senderEmail;
        this.senderKnoxId = senderKnoxId;
    }

    @Override
    public void sendOtpEmail(String to, String otpType, String otp, String subject) {
        EmailManagement record = emailRepository.save(EmailManagement.builder()
                .email(to)
                .emailType(otpType)
                .subject(subject)
                .status("PENDING")
                .build());
        try {
            String message = "EMAIL_VERIFICATION".equals(otpType)
                    ? "Use the verification code below to complete your registration."
                    : "Use the verification code below to reset your password.";
            String html = renderOtpTemplate(otp, message);
            String mailId = mailClient.sendMail(senderKnoxId, subject, html, "HTML", senderEmail, List.of(to));
            log.info("Knox Mail API sent mail to {} (mailId={})", to, mailId);
            record.setStatus("SENT");
            record.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to send OTP email via Knox Mail API to {}", to, e);
            record.setStatus("FAILED");
            record.setErrorCode("KNOX_MAIL_SEND_FAILED");
            record.setErrorMessage(truncate(e.getMessage(), 500));
        }
        emailRepository.save(record);
    }

    @Override
    public void sendNotificationEmail(String to, String subject, String body) {
        EmailManagement record = emailRepository.save(EmailManagement.builder()
                .email(to)
                .emailType("SKILL_NOTIFICATION")
                .subject(subject)
                .status("PENDING")
                .build());
        try {
            String mailId = mailClient.sendMail(senderKnoxId, subject, body, "TEXT", senderEmail, List.of(to));
            log.info("Knox Mail API sent mail to {} (mailId={})", to, mailId);
            record.setStatus("SENT");
            record.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to send notification email via Knox Mail API to {}", to, e);
            record.setStatus("FAILED");
            record.setErrorCode("KNOX_MAIL_SEND_FAILED");
            record.setErrorMessage(truncate(e.getMessage(), 500));
        }
        emailRepository.save(record);
    }

    /** Renders the Dark Prism HTML template (email/otp-template.html) with the given OTP and message. */
    private String renderOtpTemplate(String otp, String message) {
        String template = loadTemplate();
        return template.replace("{{OTP}}", otp).replace("{{MESSAGE}}", message);
    }

    private String loadTemplate() {
        try {
            return new String(new ClassPathResource("email/otp-template.html")
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load OTP email template", e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
