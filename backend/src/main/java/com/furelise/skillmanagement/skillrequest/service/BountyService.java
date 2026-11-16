package com.furelise.skillmanagement.skillrequest.service;

import com.furelise.skillmanagement.skillrequest.dto.BountyClaimResponse;
import com.furelise.skillmanagement.skillrequest.dto.BountyResponse;

import java.util.List;

/**
 * Skill Market bounty (guide §6, Phần 06). Rules:
 * <ul>
 *   <li>The requester decides; no admin in the main flow (dispute is the exception).</li>
 *   <li>Points are locked (escrow) at posting; released on accept; auto-awarded
 *       when the requester stays silent past the decision deadline; refunded
 *       when the bounty expires with no qualifying claim.</li>
 *   <li>BUILD a new skill = 100% to the claimant. MATCH an existing skill =
 *       70% to the skill author / 30% to the referrer. Referring your own
 *       skill to someone else's request = 100%. The ONLY ban is claiming
 *       your own request's bounty.</li>
 * </ul>
 */
public interface BountyService {

    /** Minimum / maximum stake (points). */
    int MIN_BOUNTY = 50;
    int MAX_BOUNTY = 5000;

    /** Bounty lifetime: 14 days from posting. */
    int BOUNTY_DAYS = 14;

    /** Decision window per claim: 7 days from the claim. */
    int DECIDE_DAYS = 7;

    /** Author share of a MATCH claim (the rest goes to the referrer). */
    int MATCH_AUTHOR_SHARE_PERCENT = 70;

    /** Stake points on a request (escrowed immediately). */
    BountyResponse postBounty(Long requestId, Long userId, int points);

    /** Answer a bounty with a skill (BUILD = own new skill, MATCH = existing skill). */
    BountyClaimResponse claim(Long requestId, Long skillId, Long claimantId);

    /** All claims of a request, earliest first. */
    List<BountyClaimResponse> claims(Long requestId);

    /** Requester accepts a claim — points move, request is fulfilled. */
    BountyClaimResponse acceptClaim(Long claimId, Long requesterId);

    /** Requester rejects a claim (with the reason kept in the audit log). */
    BountyClaimResponse rejectClaim(Long claimId, Long requesterId);

    /** Either party disputes — claim goes to the admin queue. */
    BountyClaimResponse disputeClaim(Long claimId, Long userId);

    /** Nightly job: auto-award overdue decisions, refund expired bounties. */
    void expireBounties();
}
