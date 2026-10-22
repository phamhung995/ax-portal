package com.skillmanagement.skill.dto;

import java.time.Instant;

/** One comment row (Detail SRS §5.6). */
public record SkillCommentItem(
        Long id,
        Long userId,
        String userName,
        String content,
        Instant createdAt,
        boolean canDelete
) {
}
