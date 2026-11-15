package com.furelise.skillmanagement.mcp.model;

import com.furelise.skillmanagement.mcp.dto.McpPendingMetadata;
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
 * MCP master data (MCP Management — rebuilt 2026-11 on the Skill model).
 * status: DRAFT / PENDING_APPROVAL / APPROVED / REJECTED.
 * Visibility: PUBLIC only — no scope. Everyone sees APPROVED MCPs;
 * owner/admin see all statuses.
 * The CURRENT version is this row (name/description/hashtags + live files);
 * superseded versions are snapshotted into mcp_version.
 * Soft delete via deletedYn ('Y' = deleted, hidden from grid, not restorable).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "mcp_master")
public class McpMaster {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable code (MCP-0001), unique, never reused. */
    @Column(name = "mcp_code", nullable = false, length = 50)
    private String mcpCode;

    @Column(name = "mcp_name", nullable = false, length = 200)
    private String mcpName;

    /** Slug key (immutable, unique) — used by skill.md frontmatter pins. */
    @Column(name = "server_key", nullable = false, length = 100)
    private String serverKey;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = STATUS_DRAFT;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> hashtags;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    /** Version number, incremented on every approved version update. */
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    /**
     * Staged metadata for a pending version update (null = none).
     * Set when a version update is submitted; cleared on approve/reject/cancel.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_metadata", columnDefinition = "jsonb")
    private McpPendingMetadata pendingMetadata;

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
     * True when the MCP is pending a VERSION UPDATE (staged metadata).
     * Such an MCP still serves its approved version to other users — it is
     * treated as live (visible, downloadable, rateable) until the update is
     * approved, rejected or cancelled.
     */
    public boolean isVersionUpdatePending() {
        return STATUS_PENDING_APPROVAL.equals(status) && pendingMetadata != null;
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
