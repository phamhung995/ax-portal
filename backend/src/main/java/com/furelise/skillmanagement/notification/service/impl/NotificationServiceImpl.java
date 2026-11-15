package com.furelise.skillmanagement.notification.service.impl;

import com.furelise.skillmanagement.notification.dto.NotificationItem;
import com.furelise.skillmanagement.notification.model.Notification;
import com.furelise.skillmanagement.notification.repository.NotificationRepository;
import com.furelise.skillmanagement.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
    public List<NotificationItem> page(Long userId, int limit, Long cursorId) {
        Long beforeId = cursorId == null ? Long.MAX_VALUE : cursorId;
        List<Notification> rows = notificationRepository
                .findByUserIdAndNotificationIdLessThanOrderByCreatedAtDescNotificationIdDesc(
                        userId, beforeId, PageRequest.of(0, limit));
        return rows.stream().map(this::toItem).toList();
    }

    private NotificationItem toItem(Notification n) {
        return new NotificationItem(
                n.getNotificationId(), n.getTitle(), n.getSkillId(),
                n.getFeedbackId(), n.getRequestId(), n.getNewsId(),
                n.getCommentId(), n.getMcpId(), n.getCreatedAt(), n.isRead());
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
