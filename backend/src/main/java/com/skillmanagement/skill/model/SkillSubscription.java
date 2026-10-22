package com.skillmanagement.skill.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Subscription: a user follows a PUBLISHED skill to receive new-version
 * notifications. Composite PK (skill_id, user_id).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_subscriptions")
@IdClass(SkillSubscription.SkillSubscriptionId.class)
public class SkillSubscription {

    @Id
    @Column(name = "skill_id", nullable = false)
    private Long skillId;

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
    public static class SkillSubscriptionId implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long skillId;
        private Long userId;
    }
}
