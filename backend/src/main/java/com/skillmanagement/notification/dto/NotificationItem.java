package com.skillmanagement.notification.dto;

import java.time.Instant;

/**
 * Notification row for the GNB bell panel (Home SRS §7.5).
 */
public record NotificationItem(
        Long notificationId,
        String title,
        String skillId,
        Long feedbackId,
        Instant createdAt,
        boolean isRead
) {
}
