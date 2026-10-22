package com.skillmanagement.skill.dto;

/**
 * Payload for renaming / re-describing an existing process stage (admin).
 */
public record SkillTaxonomyUpdateRequest(
        String name,
        String description
) {
}
