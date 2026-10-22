package com.skillmanagement.skill.dto;

/**
 * Payload for creating a category or a process stage (admin).
 */
public record SkillTaxonomyCreateRequest(
        String name,
        String description
) {
}
