package com.skillmanagement.skillrequest.dto;

import java.util.List;

/**
 * Aggregated stats for the skill forum sidebar cards.
 * Each list is capped at 10 entries, pre-sorted by its ranking.
 */
public record SkillRequestStatsResult(
        List<TopVotedItem> topVoted,
        List<TopRequesterItem> topRequesters,
        List<PopularHashtagItem> popularHashtags
) {

    /** A request ranked by vote count (desc), then comment count, then newest. */
    public record TopVotedItem(
            Long id,
            String title,
            int voteCount,
            int commentCount
    ) {
    }

    /** A user ranked by number of requests created (desc), then resolved count. */
    public record TopRequesterItem(
            Long userId,
            String userName,
            int requestCount,
            int resolvedCount
    ) {
    }

    /** A hashtag ranked by number of requests carrying it (desc). */
    public record PopularHashtagItem(
            String tag,
            int count
    ) {
    }
}
