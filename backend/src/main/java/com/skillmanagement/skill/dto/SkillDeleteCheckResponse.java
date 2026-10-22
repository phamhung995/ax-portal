package com.skillmanagement.skill.dto;

import java.util.List;

/**
 * Pre-delete validation (grid bulk delete): the UI calls this before the
 * confirm dialog so it can show which selected skills cannot be deleted.
 */
public record SkillDeleteCheckResponse(
        boolean canDelete,
        List<DeniedSkill> deniedSkills
) {
    public record DeniedSkill(Long id, String name, String creatorName) {
    }
}
