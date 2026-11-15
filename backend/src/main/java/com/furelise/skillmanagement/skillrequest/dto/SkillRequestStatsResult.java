package com.furelise.skillmanagement.skillrequest.dto;

import java.util.List;

/**
 * Aggregated stats for the skill forum sidebar cards.
 * topVoted: last 7 days, capped at 10.
 * topContributors: all time, capped at 5.
 * popularHashtags: last 30 days, capped at 10.
 */
public record SkillRequestStatsResult(
        List<TopVotedItem> topVoted,
        List<TopContributorItem> topContributors,
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

    /** A user ranked by number of skill links in comments (desc), then name. */
    public record TopContributorItem(
            Long userId,
            String userName,
            String userAvatarUrl,
            int skillLinkCount
    ) {
    }

    /** A hashtag ranked by number of requests carrying it (desc). */
    public record PopularHashtagItem(
            String tag,
            int count
    ) {
    }
}
