package com.furelise.skillmanagement.notification.service;

import com.furelise.skillmanagement.mail.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Asynchronous notification email sender.
 *
 * <p>Notification emails go through the Knox Mail API (blocking network I/O,
 * 2 DB writes per mail). Sending them inline would hold the caller's
 * transaction open for the whole round-trip — a slow Knox + many @mentions
 * would stall the comment request and occupy a pooled DB connection. The
 * email is best-effort (a failure only logs), so it is safe to fire and
 * forget on the task executor.</p>
 *
 * <p>Only the EMAIL is async — the in-app notification row is written
 * synchronously by {@code EventNotificationServiceImpl} inside the caller's
 * (or its own) transaction.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEmailSender {

    private final EmailService emailService;

    @Async
    public void sendAsync(Long userId, String to, String subject, String body) {
        try {
            emailService.sendNotificationEmail(to, subject, body);
        } catch (Exception e) {
            // Log the user id only — the recipient's email address is PII
            // and must not land in the logs.
            log.warn("Không thể gửi email notification cho user {}: {}",
                    userId, e.getMessage());
        }
    }
}
