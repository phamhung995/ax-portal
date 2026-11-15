package com.furelise.skillmanagement.skillrequest.dto;

import java.time.Instant;
import java.util.List;

/**
 * One feed page of skill requests, ranked by
 * {@code (voteCount*2 + commentCount) * timeDecay} (newest/hottest first).
 */
public record SkillRequestFeedResult(
        List<SkillRequestFeedItem> items,
        String nextCursor,
        boolean hasMore
) {
}
