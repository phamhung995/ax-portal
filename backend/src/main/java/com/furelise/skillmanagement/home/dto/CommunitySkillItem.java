package com.furelise.skillmanagement.home.dto;

import java.util.List;

/**
 * A skill row in the community cards (Home SRS §7.2).
 * ratingScore = null when the skill has no rating (UI shows "No rating").
 * categories / stages = taxonomy names of the skill (empty when unmapped).
 */
public record CommunitySkillItem(
        Long skillId,
        String skillName,
        String creatorName,
        String status,
        java.time.Instant createdAt,
        Integer viewCount,
        Double ratingScore,
        Integer ratingCount,
        List<String> categories,
        List<String> stages
) {
}
