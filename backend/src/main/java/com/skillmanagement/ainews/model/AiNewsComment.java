package com.skillmanagement.ainews.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Threaded comment on an AI news item (AI_News_Screen_SRS.md §6.1).
 * parent_id = null → top-level. Soft delete via deletedAt.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ai_news_comment")
public class AiNewsComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commentId;

    @Column(name = "news_id", nullable = false)
    private Long newsId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** null = top-level. */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "helpful_count", nullable = false)
    @Builder.Default
    private Integer helpfulCount = 0;

    @Column(name = "reply_count", nullable = false)
    @Builder.Default
    private Integer replyCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Soft delete timestamp (null = not deleted). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (helpfulCount == null) {
            helpfulCount = 0;
        }
        if (replyCount == null) {
            replyCount = 0;
        }
    }
}
