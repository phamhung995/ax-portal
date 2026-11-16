package com.furelise.skillmanagement.skillrequest.dto;

import java.time.Instant;

/** One claim on a request's bounty. */
public record BountyClaimResponse(
        Long id,
        Long requestId,
        Long skillId,
        String skillName,
        Long claimantId,
        String claimantName,
        String claimType,
        String status,
        Instant decideBy,
        Instant createdAt
) {
}
