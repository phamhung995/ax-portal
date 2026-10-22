package com.skillmanagement.skillrequest.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * One permanent vote per user on a skill request (no unvote).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_request_votes")
@IdClass(SkillRequestVote.SkillRequestVoteId.class)
public class SkillRequestVote {

    @Id
    @Column(name = "request_id", nullable = false)
    private Long requestId;

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
    public static class SkillRequestVoteId implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long requestId;
        private Long userId;
    }
}
