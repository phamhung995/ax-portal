package com.skillmanagement.skill.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * View log for idempotent view counting: one user counts once per skill
 * per view window (configurable, default 15 minutes).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_view_log")
public class SkillViewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "viewed_at", nullable = false, updatable = false)
    private Instant viewedAt;

    @PrePersist
    protected void onCreate() {
        if (viewedAt == null) {
            viewedAt = Instant.now();
        }
    }
}
