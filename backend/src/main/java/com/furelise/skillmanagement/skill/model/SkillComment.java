package com.furelise.skillmanagement.skill.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Comment on a skill (max 1000 chars). Soft delete via deletedAt.
 * 2026-11-08: threaded (depth 2) + "Helpful" likes + edit (updated_at).
 * parent_id = null → top-level; a reply points at its top-level comment.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_comments")
public class SkillComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** null = top-level, else the id of the top-level comment (depth 2 only). */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private Integer likeCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Set when the content is edited. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Soft delete: NULL = not deleted. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (likeCount == null) {
            likeCount = 0;
        }
    }
}
