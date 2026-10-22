package com.skillmanagement.skillrequest.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * One like per user on a skill request comment (toggle: like / unlike).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_request_comment_likes")
@IdClass(SkillRequestCommentLike.SkillRequestCommentLikeId.class)
public class SkillRequestCommentLike {

    @Id
    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Id
    @Column(name = "user_id", nullable = false)
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
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SkillRequestCommentLikeId implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long commentId;
        private Long userId;
    }
}
