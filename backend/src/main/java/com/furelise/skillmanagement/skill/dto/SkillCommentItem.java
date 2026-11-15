package com.furelise.skillmanagement.skill.dto;

import java.time.Instant;
import java.util.List;

/**
 * One comment row (Detail SRS §5.6).
 * 2026-11-08: threaded (depth 2) + "Helpful" likes + edit.
 * Replies are nested under their top-level comment (chronological).
 */
public record SkillCommentItem(
        Long id,
        Long userId,
        String userName,
        String avatarUrl,
        Long parentId,
        String content,
        int likeCount,
        int replyCount,
        Instant createdAt,
        Instant updatedAt,
        boolean own,
        boolean liked,
        boolean canDelete,
        boolean canEdit,
        List<SkillCommentItem> replies
) {
}
