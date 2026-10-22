package com.skillmanagement.skill.dto;

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
        String rejectionReason,
        List<String> hashtags,
        List<SkillFileItem> files,
        int viewCount,
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
        SkillPendingVersionItem pendingVersion,
        List<String> categories,
        List<String> stages
) {
}
