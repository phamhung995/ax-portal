package com.furelise.skillmanagement.skillrequest.service.impl;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.gamification.service.GamificationService;
import com.furelise.skillmanagement.reputation.service.ReputationService;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skillrequest.dto.BountyClaimResponse;
import com.furelise.skillmanagement.skillrequest.dto.BountyResponse;
import com.furelise.skillmanagement.skillrequest.model.BountyClaim;
import com.furelise.skillmanagement.skillrequest.model.SkillRequest;
import com.furelise.skillmanagement.skillrequest.repository.BountyClaimRepository;
import com.furelise.skillmanagement.skillrequest.repository.SkillRequestRepository;
import com.furelise.skillmanagement.skillrequest.service.BountyService;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BountyServiceImpl implements BountyService {

    private final SkillRequestRepository requestRepository;
    private final BountyClaimRepository claimRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final GamificationService gamificationService;
    private final ReputationService reputationService;

    @Override
    @Transactional
    public BountyResponse postBounty(Long requestId, Long userId, int points) {
        SkillRequest request = requestRepository.findById(requestId).orElseThrow(() ->
                new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_FOUND, "Request not found."));
        if (!request.getCreatedBy().equals(userId)) {
            throw new BusinessException(BusinessErrorCode.BOUNTY_NOT_REQUESTER,
                    "Only the requester can post a bounty.");
        }
        if (!"NONE".equals(request.getBountyStatus())) {
            throw new BusinessException(BusinessErrorCode.BOUNTY_NOT_ACTIVE,
                    "This request already has a bounty.");
        }
        if (points < MIN_BOUNTY || points > MAX_BOUNTY) {
            throw new BusinessException(BusinessErrorCode.BOUNTY_INVALID_AMOUNT,
                    "Bounty must be between " + MIN_BOUNTY + " and " + MAX_BOUNTY + " points.");
        }
        gamificationService.spendPoints(userId, points, "BOUNTY_LOCK",
                "Treo thưởng cho request #" + requestId, "SKILL_REQUEST", requestId);
        request.setBountyPoints(points);
        request.setBountyStatus("ACTIVE");
        request.setBountyExpiresAt(Instant.now().plus(BOUNTY_DAYS, ChronoUnit.DAYS));
        requestRepository.save(request);
        return toResponse(request);
    }

    @Override
    @Transactional
    public BountyClaimResponse claim(Long requestId, Long skillId, Long claimantId) {
        SkillRequest request = requestRepository.findById(requestId).orElseThrow(() ->
                new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_FOUND, "Request not found."));
        if (!"ACTIVE".equals(request.getBountyStatus())) {
            throw new BusinessException(BusinessErrorCode.BOUNTY_NOT_ACTIVE,
                    "This request has no active bounty.");
        }
        if (request.getCreatedBy().equals(claimantId)) {
            // The ONLY ban: claiming your own request's bounty.
            throw new BusinessException(BusinessErrorCode.BOUNTY_OWN_REQUEST,
                    "You cannot claim the bounty on your own request.");
        }
        Skill skill = skillRepository.findById(skillId).orElseThrow(() ->
                new BusinessException(BusinessErrorCode.SKILL_NOT_FOUND, "Skill not found."));
        if (!Skill.STATUS_APPROVED.equals(skill.getApprovalStatus())) {
            throw new BusinessException(BusinessErrorCode.BOUNTY_SKILL_NOT_APPROVED,
                    "Only APPROVED skills can fulfil a bounty.");
        }
        if (claimRepository.findByRequestIdAndSkillId(requestId, skillId).isPresent()) {
            throw new BusinessException(BusinessErrorCode.BOUNTY_ALREADY_CLAIMED,
                    "This skill already claims this bounty.");
        }
        String claimType = skill.getCreatedBy().equals(claimantId) ? "BUILD" : "MATCH";
        BountyClaim claim = claimRepository.save(BountyClaim.builder()
                .requestId(requestId)
                .skillId(skillId)
                .claimantId(claimantId)
                .claimType(claimType)
                .decideBy(Instant.now().plus(DECIDE_DAYS, ChronoUnit.DAYS))
                .build());
        return toClaimResponse(claim, skill);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BountyClaimResponse> claims(Long requestId) {
        SkillRequest request = requestRepository.findById(requestId).orElseThrow(() ->
                new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_FOUND, "Request not found."));
        Map<Long, Skill> skills = skillRepository.findByIdIn(
                        claimRepository.findByRequestIdOrderByCreatedAtAsc(request.getId()).stream()
                                .map(BountyClaim::getSkillId).toList()).stream()
                .collect(LinkedHashMap::new, (m, s) -> m.put(s.getId(), s), Map::putAll);
        return claimRepository.findByRequestIdOrderByCreatedAtAsc(requestId).stream()
                .map(c -> toClaimResponse(c, skills.get(c.getSkillId())))
                .toList();
    }

    @Override
    @Transactional
    public BountyClaimResponse acceptClaim(Long claimId, Long requesterId) {
        BountyClaim claim = claimRepository.findById(claimId).orElseThrow(() ->
                new BusinessException(BusinessErrorCode.BOUNTY_CLAIM_NOT_FOUND, "Claim not found."));
        SkillRequest request = requestRepository.findById(claim.getRequestId()).orElseThrow(() ->
                new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_FOUND, "Request not found."));
        if (!request.getCreatedBy().equals(requesterId)) {
            throw new BusinessException(BusinessErrorCode.BOUNTY_NOT_REQUESTER,
                    "Only the requester can accept a claim.");
        }
        if (!"PENDING".equals(claim.getStatus())) {
            throw new BusinessException(BusinessErrorCode.BOUNTY_CLAIM_NOT_PENDING,
                    "This claim was already decided.");
        }
        acceptClaimInternal(claim, request);
        return toClaimResponse(claim, skillRepository.findById(claim.getSkillId()).orElse(null));
    }

    @Override
    @Transactional
    public BountyClaimResponse rejectClaim(Long claimId, Long requesterId) {
        BountyClaim claim = claimRepository.findById(claimId).orElseThrow(() ->
                new BusinessException(BusinessErrorCode.BOUNTY_CLAIM_NOT_FOUND, "Claim not found."));
        SkillRequest request = requestRepository.findById(claim.getRequestId()).orElseThrow(() ->
                new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_FOUND, "Request not found."));
        if (!request.getCreatedBy().equals(requesterId)) {
            throw new BusinessException(BusinessErrorCode.BOUNTY_NOT_REQUESTER,
                    "Only the requester can reject a claim.");
        }
        if (!"PENDING".equals(claim.getStatus())) {
            throw new BusinessException(BusinessErrorCode.BOUNTY_CLAIM_NOT_PENDING,
                    "This claim was already decided.");
        }
        claim.setStatus("REJECTED");
        claim.setDecidedAt(Instant.now());
        claimRepository.save(claim);
        return toClaimResponse(claim, skillRepository.findById(claim.getSkillId()).orElse(null));
    }

    @Override
    @Transactional
    public BountyClaimResponse disputeClaim(Long claimId, Long userId) {
        BountyClaim claim = claimRepository.findById(claimId).orElseThrow(() ->
                new BusinessException(BusinessErrorCode.BOUNTY_CLAIM_NOT_FOUND, "Claim not found."));
        SkillRequest request = requestRepository.findById(claim.getRequestId()).orElseThrow(() ->
                new BusinessException(BusinessErrorCode.SKILL_REQUEST_NOT_FOUND, "Request not found."));
        boolean party = request.getCreatedBy().equals(userId) || claim.getClaimantId().equals(userId);
        if (!party) {
            throw new BusinessException(BusinessErrorCode.BOUNTY_NOT_REQUESTER,
                    "Only the requester or the claimant can dispute.");
        }
        if (!"PENDING".equals(claim.getStatus())) {
            throw new BusinessException(BusinessErrorCode.BOUNTY_CLAIM_NOT_PENDING,
                    "This claim was already decided.");
        }
        claim.setStatus("DISPUTED");
        claim.setDecidedAt(Instant.now());
        claimRepository.save(claim);
        return toClaimResponse(claim, skillRepository.findById(claim.getSkillId()).orElse(null));
    }

    /**
     * Nightly: (1) auto-award PENDING claims whose decision deadline passed
     * (requester silent — objective criteria: APPROVED skill, earliest claim
     * wins); (2) refund ACTIVE bounties past expiry with no accepted claim.
     */
    @Override
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void expireBounties() {
        Instant now = Instant.now();
        // (1) Overdue decisions — earliest qualifying claim per request.
        Map<Long, BountyClaim> earliestByRequest = new LinkedHashMap<>();
        for (BountyClaim claim : claimRepository.findByStatusAndDecideByBefore("PENDING", now)) {
            earliestByRequest.putIfAbsent(claim.getRequestId(), claim);
        }
        for (BountyClaim claim : earliestByRequest.values()) {
            SkillRequest request = requestRepository.findById(claim.getRequestId()).orElse(null);
            Skill skill = skillRepository.findById(claim.getSkillId()).orElse(null);
            if (request != null && skill != null && "ACTIVE".equals(request.getBountyStatus())
                    && Skill.STATUS_APPROVED.equals(skill.getApprovalStatus())) {
                acceptClaimInternal(claim, request);
            }
        }
        // (2) Expired bounties — refund the escrow to the requester.
        for (SkillRequest request : requestRepository.findByBountyStatusAndBountyExpiresAtBefore("ACTIVE", now)) {
            if (claimRepository.countByRequestIdAndStatus(request.getId(), "ACCEPTED") > 0) {
                continue;
            }
            gamificationService.awardPoints(request.getCreatedBy(), request.getBountyPoints(),
                    "BOUNTY_REFUND", "Hoàn thưởng request #" + request.getId()
                            + " (hết hạn, không có đáp ứng hợp lệ)",
                    "SKILL_REQUEST", request.getId());
            request.setBountyStatus("REFUNDED");
            requestRepository.save(request);
        }
    }

    /** Shared by requester-accept and the nightly auto-award. */
    private void acceptClaimInternal(BountyClaim claim, SkillRequest request) {
        Skill skill = skillRepository.findById(claim.getSkillId()).orElseThrow(() ->
                new BusinessException(BusinessErrorCode.SKILL_NOT_FOUND, "Skill not found."));
        int points = request.getBountyPoints();
        boolean ownSkill = skill.getCreatedBy().equals(claim.getClaimantId());
        if ("BUILD".equals(claim.getClaimType()) || ownSkill) {
            // 100% to the claimant (builder, or author referring their own skill).
            gamificationService.awardPoints(claim.getClaimantId(), points, "BOUNTY_AWARD",
                    "Thưởng đáp ứng request #" + request.getId(), "SKILL_REQUEST", request.getId());
        } else {
            // MATCH someone else's skill: 70% author / 30% referrer.
            int authorShare = points * MATCH_AUTHOR_SHARE_PERCENT / 100;
            int referrerShare = points - authorShare;
            gamificationService.awardPoints(skill.getCreatedBy(), authorShare, "BOUNTY_AWARD",
                    "Thưởng skill đáp ứng request #" + request.getId(), "SKILL_REQUEST", request.getId());
            gamificationService.awardPoints(claim.getClaimantId(), referrerShare, "BOUNTY_AWARD",
                    "Phí giới thiệu skill cho request #" + request.getId(), "SKILL_REQUEST", request.getId());
            reputationService.refreshContributor(skill.getCreatedBy());
        }
        // Base action reward for fulfilling a request (guide §1.1, +80).
        gamificationService.awardPoints(claim.getClaimantId(), GamificationService.POINTS_FULFILL_REQUEST,
                "FULFILL_REQUEST", "Đáp ứng request #" + request.getId(), "SKILL_REQUEST", request.getId());
        reputationService.refreshContributor(claim.getClaimantId());
        reputationService.refreshSkill(skill.getId());

        claim.setStatus("ACCEPTED");
        claim.setDecidedAt(Instant.now());
        claimRepository.save(claim);
        // Other pending claims on this request are closed.
        for (BountyClaim other : claimRepository.findByRequestIdOrderByCreatedAtAsc(request.getId())) {
            if (!other.getId().equals(claim.getId()) && "PENDING".equals(other.getStatus())) {
                other.setStatus("REJECTED");
                other.setDecidedAt(Instant.now());
                claimRepository.save(other);
            }
        }
        request.setBountyStatus("FULFILLED");
        request.setFulfilledSkillId(skill.getId());
        request.setFulfilledBy(claim.getClaimantId());
        request.setBountyAwardedAt(Instant.now());
        requestRepository.save(request);
    }

    private BountyResponse toResponse(SkillRequest request) {
        return new BountyResponse(request.getId(), request.getBountyPoints(),
                request.getBountyStatus(), request.getBountyExpiresAt(),
                request.getFulfilledSkillId(), request.getFulfilledBy(),
                request.getBountyAwardedAt());
    }

    private BountyClaimResponse toClaimResponse(BountyClaim claim, Skill skill) {
        String skillName = skill != null ? skill.getName() : null;
        String claimantName = userRepository.findById(claim.getClaimantId())
                .map(User::getName).orElse(null);
        return new BountyClaimResponse(claim.getId(), claim.getRequestId(), claim.getSkillId(),
                skillName, claim.getClaimantId(), claimantName, claim.getClaimType(),
                claim.getStatus(), claim.getDecideBy(), claim.getCreatedAt());
    }
}
