package com.furelise.skillmanagement.skillrequest.dto;

import java.time.Instant;

/** Bounty state of a request, as shown in the feed. */
public record BountyResponse(
        Long requestId,
        int bountyPoints,
        String bountyStatus,
        Instant bountyExpiresAt,
        Long fulfilledSkillId,
        Long fulfilledBy,
        Instant bountyAwardedAt
) {
}
