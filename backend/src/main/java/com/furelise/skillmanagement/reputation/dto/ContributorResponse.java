package com.furelise.skillmanagement.reputation.dto;

import java.math.BigDecimal;

/**
 * One contributor's (author's) reputation — derived from the reputation of
 * the items they created, plus fulfilled requests and reports. Kept SEPARATE
 * from item scores: an author's tier never adds points to their items
 * (BA §9.3, P3).
 */
public record ContributorResponse(
        Long userId,
        String userName,
        String avatarUrl,
        /** NEW_MEMBER | VERIFIED | TRUSTED_AUTHOR | EXPERT (own names, not content tiers). */
        String tier,
        int itemCount,
        BigDecimal avgScore,
        int fulfilledRequests,
        int reportCount
) {
}
