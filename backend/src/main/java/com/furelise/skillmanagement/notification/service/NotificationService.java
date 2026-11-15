package com.furelise.skillmanagement.notification.service;

import com.furelise.skillmanagement.notification.dto.NotificationItem;

import java.util.List;

/**
 * In-app notifications for the GNB bell (Home SRS §4.7).
 * The full Notification Center is out of scope (separate SRS).
 */
public interface NotificationService {

    long unreadCount(Long userId);

    /**
     * Lazy-load page for the bell panel: up to {@code limit} notifications
     * strictly older than {@code cursorId} (null = first page).
     */
    List<NotificationItem> page(Long userId, int limit, Long cursorId);

    /** Marks one notification as read (by id, must belong to the user). */
    void markRead(Long userId, Long notificationId);

    void markAllRead(Long userId);
}
