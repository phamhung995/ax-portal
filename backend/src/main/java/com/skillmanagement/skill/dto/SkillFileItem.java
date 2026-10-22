package com.skillmanagement.skill.dto;

import java.time.Instant;

/**
 * A file attached to a skill (detail popup + edit popup).
 */
public record SkillFileItem(
        Long id,
        String fileName,
        Long fileSize,
        Instant createdAt
) {
}
