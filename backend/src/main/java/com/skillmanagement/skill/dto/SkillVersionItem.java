package com.skillmanagement.skill.dto;

import java.time.Instant;
import java.util.List;

/**
 * One row of the skill Versions tab (a superseded, review-approved version).
 * The current version is the skill itself and is not listed here.
 */
public record SkillVersionItem(
        int version,
        String name,
        String description,
        String publicScope,
        List<String> hashtags,
        String creatorName,
        Instant createdAt,
        List<SkillVersionFileItem> files
) {}
