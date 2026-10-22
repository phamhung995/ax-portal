package com.skillmanagement.skill.dto;

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
        List<String> categories,
        List<String> stages,
        int viewCount,
        Double avgRating,
        long ratingCount,
        boolean subscribed,
        boolean hasPendingVersion
) {
}
