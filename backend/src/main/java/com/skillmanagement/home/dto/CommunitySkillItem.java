package com.skillmanagement.home.dto;

/**
 * A skill row in the community cards (Home SRS §7.2).
 * ratingScore = null when the skill has no rating (UI shows "No rating").
 */
public record CommunitySkillItem(
        Long skillId,
        String skillName,
        String creatorName,
        String status,
        java.time.Instant createdAt,
        Integer viewCount,
        Double ratingScore,
        Integer ratingCount
) {
}
