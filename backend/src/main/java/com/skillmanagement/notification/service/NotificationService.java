package com.skillmanagement.notification.service;

import com.skillmanagement.notification.dto.NotificationItem;

import java.util.List;

/**
 * In-app notifications for the GNB bell (Home SRS §4.7).
 * The full Notification Center is out of scope (separate SRS).
 */
public interface NotificationService {

    long unreadCount(Long userId);

    List<NotificationItem> latest(Long userId, int limit);

    /** Marks one notification as read (by id, must belong to the user). */
    void markRead(Long userId, Long notificationId);

    void markAllRead(Long userId);
}
