package com.skillmanagement.skill.dto;

/**
 * One row of the "Manage Categories & Stages" admin modal:
 * the master entry plus how many (not-deleted) skills use it.
 */
public record SkillTaxonomyItem(
        Long id,
        String name,
        int sortOrder,
        String description,
        long skillCount
) {
}
