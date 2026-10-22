package com.skillmanagement.notification.service.impl;

import com.skillmanagement.notification.dto.NotificationItem;
import com.skillmanagement.notification.model.Notification;
import com.skillmanagement.notification.repository.NotificationRepository;
import com.skillmanagement.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationItem> latest(Long userId, int limit) {
        return notificationRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(limit)
                .map(n -> new NotificationItem(
                        n.getNotificationId(), n.getTitle(), n.getSkillId(),
                        n.getFeedbackId(), n.getCreatedAt(), n.isRead()))
                .toList();
    }

    @Override
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findById(notificationId).orElse(null);
        if (n == null || !n.getUserId().equals(userId)) {
            return; // never expose other users' notifications
        }
        n.setRead(true);
        notificationRepository.save(n);
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }
}
