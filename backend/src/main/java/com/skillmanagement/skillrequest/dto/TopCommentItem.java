package com.skillmanagement.skillrequest.dto;

import java.time.Instant;

/** Comment preview embedded in a feed item (2 newest top-level comments). */
public record TopCommentItem(
        Long id,
        Long userId,
        String userName,
        String content,
        Instant createdAt,
        Long linkedSkillId,
        String linkedSkillName,
        String linkedSkillStatus,
        String resolvedYn,
        int likeCount,
        boolean likedByMe
) {
}
