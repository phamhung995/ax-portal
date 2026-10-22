package com.skillmanagement.skillrequest.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Comment on a skill request (max 1000 chars). Soft delete via deletedAt.
 * A comment may link to one existing skill (linkedSkillId); the request
 * creator (or an admin) can mark such a comment as resolved.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_request_comments")
public class SkillRequestComment {

    public static final String RESOLVED_NO = "N";
    public static final String RESOLVED_YES = "Y";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** The existing skill this comment points to (null = no link). */
    @Column(name = "linked_skill_id")
    private Long linkedSkillId;

    /** Parent comment for threaded replies (null = top-level). UI caps depth at 3. */
    @Column(name = "parent_id")
    private Long parentId;

    /** Denormalized like counter. */
    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private Integer likeCount = 0;

    /** Whether the request creator confirmed this comment resolves the request. */
    @Column(name = "resolved_yn", nullable = false, length = 1)
    @Builder.Default
    private String resolvedYn = RESOLVED_NO;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Soft delete: NULL = not deleted. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
