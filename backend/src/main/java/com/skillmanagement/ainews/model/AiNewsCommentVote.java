package com.skillmanagement.ainews.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * "Helpful" vote on a comment (AI_News_Screen_SRS.md §6.2).
 * One vote per user per comment (composite PK).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "ai_news_comment_vote")
@IdClass(AiNewsCommentVote.Id.class)
public class AiNewsCommentVote {

    @jakarta.persistence.Id
    @Column(name = "comment_id")
    private Long commentId;

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
        private Long commentId;
        private Long userId;

        public Id() {
        }

        public Id(Long commentId, Long userId) {
            this.commentId = commentId;
            this.userId = userId;
        }
    }
}
