package com.furelise.skillmanagement.notification.repository;

import com.furelise.skillmanagement.notification.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Lazy-load page: rows strictly older than the cursor (notificationId is auto-increment). */
    List<Notification> findByUserIdAndNotificationIdLessThanOrderByCreatedAtDescNotificationIdDesc(
            Long userId, Long notificationId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    int markAllRead(@Param("userId") Long userId);
}
