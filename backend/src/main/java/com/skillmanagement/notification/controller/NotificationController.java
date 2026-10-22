package com.skillmanagement.notification.controller;

import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.notification.dto.NotificationItem;
import com.skillmanagement.user.model.User;
import com.skillmanagement.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GNB notification bell endpoints (Home SRS §7.4/7.5).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("unreadCount", notificationService.unreadCount(user.getId()))));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationItem>>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.latest(user.getId(), limit)));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @AuthenticationPrincipal User user,
            @PathVariable Long notificationId) {
        notificationService.markRead(user.getId(), notificationId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(@AuthenticationPrincipal User user) {
        notificationService.markAllRead(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
