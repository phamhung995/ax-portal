package com.furelise.skillmanagement.skill.dto;

import java.time.Instant;
import java.util.List;

/**
 * One row of the Skill Management grid.
 */
public record SkillListItem(
        Long id,
        String name,
        String description,
        Long creatorId,
        String creatorName,
        Instant createdAt,
        String status,
        String scope,
        String systemKey,
        String subSystemCode,
        List<String> categories,
        List<String> stages,
        int viewCount,
        Double avgRating,
        long ratingCount,
        boolean subscribed,
        boolean hasPendingVersion,
        /** 'SHARE' | 'SCOPE' | null — why the skill is pending (null = legacy submit or version update). */
        String pendingReason,
        /** Number of shares awaiting admin approval (only when pendingReason = 'SHARE'). */
        int pendingShareCount,
        boolean isCowork,
        long downloadCount
) {
}
