package com.furelise.skillmanagement.skill.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A share of a PROJECT-scope skill (skill_share).
 *
 * <p>Two share types:
 * <ul>
 *   <li>USER — one specific user (user_id set, project_key null)</li>
 *   <li>PROJECT — everyone in one project (project_key set, user_id null)</li>
 * </ul>
 * Shared users and shared-project members see the skill in addition to the
 * normal PROJECT scope audience. Only the creator or an admin may share.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skill_share")
public class SkillShare {

    public static final String TYPE_USER = "USER";
    public static final String TYPE_PROJECT = "PROJECT";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "share_type", nullable = false, length = 10)
    private String shareType;

    /** Set when shareType = USER. */
    @Column(name = "user_id")
    private Long userId;

    /** Set when shareType = PROJECT. */
    @Column(name = "project_key", length = 100)
    private String projectKey;

    /**
     * 'PENDING' | 'APPROVED' | 'REJECTED'. External shares (a user outside
     * the creator's projects, or another project) start PENDING and only
     * grant visibility once an admin approves the skill's pending state.
     */
    @Builder.Default
    @Column(name = "approval_status", nullable = false, length = 20)
    private String approvalStatus = STATUS_APPROVED;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
