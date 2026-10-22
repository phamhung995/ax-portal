package com.skillmanagement.ainews.dto;

import java.time.Instant;

/**
 * Comment row (AI_News_Screen_SRS.md §5.6).
 * deleted = true → tombstone "[deleted]" (top-level with surviving replies).
 */
public record AiNewsCommentItem(
        Long commentId,
        Long newsId,
        Long parentId,
        String userName,
        String content,
        int helpfulCount,
        int replyCount,
        Instant createdAt,
        boolean deleted,
        boolean own,
        boolean voted
) {
}
