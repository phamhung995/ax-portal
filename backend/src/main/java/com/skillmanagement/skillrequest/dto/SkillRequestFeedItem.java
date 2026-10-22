package com.skillmanagement.skillrequest.dto;

import java.time.Instant;
import java.util.List;

/** One skill request post in the feed. */
public record SkillRequestFeedItem(
        Long id,
        String title,
        String description,
        List<String> hashtags,
        int voteCount,
        int commentCount,
        Long creatorId,
        String creatorName,
        Instant createdAt,
        boolean hasVoted,
        /** True when the viewer created this request (can resolve comments). */
        boolean isRequestCreator,
        /** True when at least one comment of this request is resolved. */
        boolean hasResolvedComment,
        /** The two newest comments (preview). */
        List<TopCommentItem> topComments,
        /** Attachments: images render in the grid, other files as download links. */
        List<SkillRequestFileItem> files
) {
}
