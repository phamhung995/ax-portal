package com.skillmanagement.home.dto;

import java.time.Instant;

/**
 * Row for My Recent Skills / Recently Updated lists (Home SRS §7.2/7.3).
 * ratingScore is null when the skill has no rating (UI shows "No rating").
 */
public record HomeSkillItem(
        String skillId,
        String skillName,
        String status,
        String creatorName,
        Instant updatedAt,
        Integer viewCount,
        Double ratingScore,
        Integer ratingCount
) {
}
