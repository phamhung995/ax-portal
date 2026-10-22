package com.skillmanagement.ainews.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Like on a news item (AI_News_Screen_SRS.md §6.3).
 * One like per user per news (composite PK). Silent toggle — no notification.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ai_news_like")
@IdClass(AiNewsLike.Id.class)
public class AiNewsLike {

    @jakarta.persistence.Id
    @Column(name = "news_id")
    private Long newsId;

    @jakarta.persistence.Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Data
    public static class Id implements java.io.Serializable {
        private Long newsId;
        private Long userId;

        public Id() {
        }

        public Id(Long newsId, Long userId) {
            this.newsId = newsId;
            this.userId = userId;
        }
    }
}
