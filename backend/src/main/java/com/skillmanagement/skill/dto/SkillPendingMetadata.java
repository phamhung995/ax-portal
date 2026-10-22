package com.skillmanagement.skill.dto;

import java.util.List;

/**
 * Staged metadata for a pending version update on an APPROVED skill.
 * Stored as JSONB in skills_master.pending_metadata; applied to the skill
 * when the update is approved.
 */
public record SkillPendingMetadata(
        String name,
        String description,
        String publicScope,
        List<String> hashtags,
        List<Long> categoryIds,
        List<Long> stageIds
) {}
