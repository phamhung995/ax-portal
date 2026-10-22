package com.skillmanagement.skill.model;

import com.skillmanagement.skill.dto.SkillPendingMetadata;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;
import java.util.List;

/**
 * Skill master data (Skill Management SRS).
 * approval_status: DRAFT / PENDING_APPROVAL / APPROVED / REJECTED.
 * public_scope: PRIVATE / PART / GROUP / PUBLIC.
 * Soft delete via deletedYn ('Y' = deleted, hidden from grid, not restorable).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "skills_master")
public class Skill {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String SCOPE_PRIVATE = "PRIVATE";
    public static final String SCOPE_PART = "PART";
    public static final String SCOPE_GROUP = "GROUP";
    public static final String SCOPE_PUBLIC = "PUBLIC";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "approval_status", nullable = false)
    @Builder.Default
    private String approvalStatus = STATUS_DRAFT;

    @Column(name = "public_scope", nullable = false)
    @Builder.Default
    private String publicScope = SCOPE_PRIVATE;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> hashtags;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    /** Version number, incremented on every successful save. */
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    /**
     * Staged metadata for a pending version update (null = none).
     * Set when a version update is submitted; cleared on approve/reject/cancel.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_metadata", columnDefinition = "jsonb")
    private SkillPendingMetadata pendingMetadata;

    /** Ids of live files to remove when the pending version update is approved. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_remove_file_ids", columnDefinition = "jsonb")
    private List<Long> pendingRemoveFileIds;

    /** Soft delete flag: 'Y' = deleted. */
    @Column(name = "deleted_yn", nullable = false, length = 1)
    @Builder.Default
    private String deletedYn = "N";

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * True when the skill is pending a VERSION UPDATE (staged metadata).
     * Such a skill still serves its approved version to other users — it is
     * treated as live (visible, downloadable, rateable) until the update is
     * approved, rejected or cancelled.
     */
    public boolean isVersionUpdatePending() {
        return STATUS_PENDING_APPROVAL.equals(approvalStatus) && pendingMetadata != null;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }
}
