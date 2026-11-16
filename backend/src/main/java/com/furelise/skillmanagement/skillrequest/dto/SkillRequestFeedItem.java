package com.furelise.skillmanagement.skillrequest.dto;

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
        String creatorAvatarUrl,
        Instant createdAt,
        boolean hasVoted,
        /** True when the viewer created this request (can resolve comments). */
        boolean isRequestCreator,
        /** True when at least one comment of this request is resolved. */
        boolean hasResolvedComment,
        /** Skills linked in resolved comments (earliest-resolved first, max 3). */
        List<ResolvedSkillItem> resolvedSkills,
        /** Total number of distinct resolved skills (may exceed resolvedSkills.size()). */
        int resolvedSkillCount,
        /** The two newest comments (preview). */
        List<TopCommentItem> topComments,
        /** Attachments: images render in the grid, other files as download links. */
        List<SkillRequestFileItem> files,
        /** Staked bounty points (0 = no bounty). */
        int bountyPoints,
        /** NONE | ACTIVE | FULFILLED | EXPIRED | REFUNDED */
        String bountyStatus,
        Instant bountyExpiresAt,
        Long fulfilledSkillId
) {
}
