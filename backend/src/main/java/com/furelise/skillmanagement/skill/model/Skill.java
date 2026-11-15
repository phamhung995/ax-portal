package com.furelise.skillmanagement.skill.model;

import com.furelise.skillmanagement.skill.dto.SkillPendingMetadata;
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
    public static final String SCOPE_PROJECT = "PROJECT";
    public static final String SCOPE_PART = "PART";
    public static final String SCOPE_GROUP = "GROUP";
    public static final String SCOPE_PUBLIC = "PUBLIC";

    /** pending_reason values on a PENDING_APPROVAL skill. */
    public static final String PENDING_REASON_SHARE = "SHARE";
    public static final String PENDING_REASON_SCOPE = "SCOPE";

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

    /**
     * Marker: the system this skill belongs to (set when scope is PROJECT).
     * Markers only — visibility comes from the creator's PIC systems, not
     * from these columns.
     */
    @Column(name = "system_key", length = 100)
    private String systemKey;

    /** Marker: the sub system this skill belongs to (optional). */
    @Column(name = "sub_system_code", length = 100)
    private String subSystemCode;

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

    /**
     * Why the skill is in PENDING_APPROVAL: 'SHARE' (external shares await
     * admin approval) or 'SCOPE' (a scope change awaits approval). Null =
     * a legacy submit-for-approval (new skill) or a version update
     * (pending_metadata set).
     */
    @Column(name = "pending_reason", length = 20)
    private String pendingReason;

    /** The new scope awaiting approval (only when pendingReason = 'SCOPE'). */
    @Column(name = "pending_scope", length = 20)
    private String pendingScope;

    /**
     * 'Y' when an admin has approved this skill at least once (permanent).
     * A reviewed skill lets its creator widen the scope and share externally
     * without further admin approval.
     */
    @Column(name = "admin_reviewed_yn", nullable = false, length = 1)
    @Builder.Default
    private String adminReviewedYn = "N";

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

    /**
     * True when the skill is in PENDING_APPROVAL for a SHARE (external
     * shares awaiting admin approval) or a SCOPE change (the old scope
     * still applies). Such a skill stays live for its current audience.
     */
    public boolean isShareOrScopePending() {
        return STATUS_PENDING_APPROVAL.equals(approvalStatus)
                && pendingMetadata == null && pendingReason != null;
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
