package com.furelise.skillmanagement.skill.dto;

import java.time.Instant;
import java.util.List;

/**
 * Skill Detail popup payload (Detail SRS §4).
 */
public record SkillDetailResponse(
        Long id,
        String name,
        String description,
        String status,
        String scope,
        String systemKey,
        String subSystemCode,
        /** 2026-11-07: resolved display names (null when the master row is gone). */
        String systemName,
        String subSystemName,
        String rejectionReason,
        List<String> hashtags,
        List<SkillFileItem> files,
        int viewCount,
        long downloadCount,
        int version,
        Long creatorId,
        String creatorName,
        Instant createdAt,
        Instant updatedAt,
        Double avgRating,
        long ratingCount,
        boolean isCreator,
        boolean isAdmin,
        boolean canEdit,
        boolean subscribed,
        boolean hasPendingVersion,
        /** 'Y' when an admin has approved this skill at least once (V91). */
        boolean adminReviewed,
        /** 'SHARE' | 'SCOPE' | null — why the skill is pending (null = legacy submit or version update). */
        String pendingReason,
        /** The new scope awaiting approval (only when pendingReason = 'SCOPE'). */
        String pendingScope,
        /** Shares awaiting admin approval (only when pendingReason = 'SHARE'). */
        List<SkillShareItem> pendingShares,
        SkillPendingVersionItem pendingVersion,
        List<String> categories,
        List<String> stages,
        List<CoworkUserItem> coworkUsers,
        /** v1.31 (FR-MCP-015): MCP dependencies for the "MCPs (n)" tab. */
        List<McpDependencyItem> mcpDependencies
) {
}
