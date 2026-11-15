package com.furelise.skillmanagement.notification.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Minimal in-app notification for the GNB bell (full model in Notification SRS).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    private Long userId;

    private String title;

    private String skillId;

    @Column(name = "feedback_id")
    private Long feedbackId;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "news_id")
    private Long newsId;

    @Column(name = "comment_id")
    private Long commentId;

    /** Deep-link to an MCP (MCP archive/scope-change notifications — BR-06). */
    @Column(name = "mcp_id")
    private Long mcpId;

    @Column(name = "is_read")
    private boolean read;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
