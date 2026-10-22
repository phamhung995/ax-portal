package com.skillmanagement.skillrequest.dto;

import java.time.Instant;
import java.util.List;

/** One comment row of a skill request. Replies are nested (max 3 levels). */
public record SkillRequestCommentItem(
        Long id,
        Long userId,
        String userName,
        String content,
        Instant createdAt,
        boolean canDelete,
        Long parentId,
        Long linkedSkillId,
        String linkedSkillName,
        String linkedSkillStatus,
        String resolvedYn,
        Instant resolvedAt,
        int likeCount,
        boolean likedByMe,
        List<SkillRequestCommentItem> replies
) {
}
