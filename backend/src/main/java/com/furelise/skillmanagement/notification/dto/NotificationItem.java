package com.furelise.skillmanagement.notification.dto;

import java.time.Instant;

/**
 * Notification row for the GNB bell panel (Home SRS §7.5).
 */
public record NotificationItem(
        Long notificationId,
        String title,
        String skillId,
        Long feedbackId,
        Long requestId,
        Long newsId,
        Long commentId,
        Long mcpId,
        Instant createdAt,
        boolean isRead
) {
}
