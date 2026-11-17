package com.furelise.skillmanagement.reputation.service.impl;

import com.furelise.skillmanagement.ainews.model.AiNews;
import com.furelise.skillmanagement.ainews.repository.AiNewsCommentVoteRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsLikeRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsViewLogRepository;
import com.furelise.skillmanagement.aitools.model.AiTool;
import com.furelise.skillmanagement.aitools.repository.AiToolDownloadLogRepository;
import com.furelise.skillmanagement.aitools.repository.AiToolRepository;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.repository.McpDownloadLogRepository;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.repository.McpRatingRepository;
import com.furelise.skillmanagement.reputation.dto.ContributorResponse;
import com.furelise.skillmanagement.reputation.dto.ReputationExplainResponse;
import com.furelise.skillmanagement.reputation.dto.ReputationResponse;
import com.furelise.skillmanagement.reputation.model.ContributorReputation;
import com.furelise.skillmanagement.reputation.model.ReputationScore;
import com.furelise.skillmanagement.reputation.model.ReputationScoreId;
import com.furelise.skillmanagement.reputation.repository.ContributorReputationRepository;
import com.furelise.skillmanagement.reputation.repository.ReputationScoreRepository;
import com.furelise.skillmanagement.reputation.service.ReputationService;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.repository.SkillDownloadLogRepository;
import com.furelise.skillmanagement.skill.repository.SkillRatingRepository;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skillrequest.model.SkillRequest;
import com.furelise.skillmanagement.skillrequest.repository.SkillRequestRepository;
import com.furelise.skillmanagement.skillrequest.repository.SkillRequestVoteRepository;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reputation core implementation (guide §2).
 * Skill score = 80% Bayesian rating quality + 20% download popularity.
 * No install events exist yet (desktop API was deleted), so success_rate
 * stays NULL until the companion app reports installs (phase 2).
 */
@Service
@RequiredArgsConstructor
public class ReputationServiceImpl implements ReputationService {

    private static final double TRUSTED_THRESHOLD = 70.0;
    private static final double POPULAR_THRESHOLD = 85.0;
    private static final int POPULAR_MIN_DOWNLOADS = 20;
    private static final double DEFAULT_GLOBAL_MEAN = 3.5;

    private final ReputationScoreRepository scoreRepository;
    private final ContributorReputationRepository contributorRepository;
    private final SkillRatingRepository ratingRepository;
    private final SkillDownloadLogRepository downloadLogRepository;
    private final SkillRepository skillRepository;
    private final McpRatingRepository mcpRatingRepository;
    private final McpDownloadLogRepository mcpDownloadLogRepository;
    private final McpMasterRepository mcpMasterRepository;
    private final AiToolRepository aiToolRepository;
    private final AiToolDownloadLogRepository aiToolDownloadLogRepository;
    private final AiNewsRepository aiNewsRepository;
    private final AiNewsLikeRepository aiNewsLikeRepository;
    private final AiNewsViewLogRepository aiNewsViewLogRepository;
    private final AiNewsCommentVoteRepository aiNewsCommentVoteRepository;
    private final SkillRequestRepository skillRequestRepository;
    private final SkillRequestVoteRepository skillRequestVoteRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ReputationScore refreshSkill(Long skillId) {
        Skill skill = skillRepository.findById(skillId).orElse(null);
        if (skill == null || "Y".equals(skill.getDeletedYn())) {
            return null;
        }
        // Q2 (BA §14, chốt 17/11): the creator's own rating is stored but
        // excluded from the score — count and average skip createdBy.
        // (The Bayesian prior mean stays system-wide; one self-rating among
        // all ratings moves it negligibly.)
        Long creatorId = skill.getCreatedBy();
        long ratingCount = creatorId == null
                ? ratingRepository.countBySkillId(skillId)
                : ratingRepository.countBySkillIdAndUserIdNot(skillId, creatorId);
        BigDecimal avg = creatorId == null
                ? ratingRepository.averageRatingBySkillId(skillId)
                : ratingRepository.averageRatingBySkillIdExcludingUser(skillId, creatorId);
        BigDecimal globalMean = ratingRepository.averageRating();
        double m = globalMean != null ? globalMean.doubleValue() : DEFAULT_GLOBAL_MEAN;
        long downloads = downloadLogRepository.countBySkillIdAndUserIdNot(skillId, skill.getCreatedBy());

        BigDecimal bayes = null;
        BigDecimal score = null;
        String tier = "NEW";
        if (ratingCount >= MIN_RATINGS && avg != null) {
            double bayesValue = (BAYESIAN_PRIOR * m + avg.doubleValue() * ratingCount)
                    / (BAYESIAN_PRIOR + ratingCount);
            bayes = BigDecimal.valueOf(bayesValue).setScale(2, RoundingMode.HALF_UP);
            double quality = bayesValue / 5.0;
            double popularity = Math.min(Math.log10(downloads + 1) / 2.0, 1.0);
            double scoreValue = (0.8 * quality + 0.2 * popularity) * 100.0;
            score = BigDecimal.valueOf(scoreValue).setScale(2, RoundingMode.HALF_UP);
            if (scoreValue >= POPULAR_THRESHOLD && downloads >= POPULAR_MIN_DOWNLOADS) {
                tier = "POPULAR";
            } else if (scoreValue >= TRUSTED_THRESHOLD) {
                tier = "TRUSTED";
            }
            // n >= 5 but score < 70: tier stays NEW and the (non-null) score is
            // shown, so the user sees "not trusted yet" instead of hiding.
        }

        ReputationScore row = scoreRepository.findById(new ReputationScoreId("SKILL", skillId))
                .orElse(ReputationScore.builder()
                        .id(new ReputationScoreId("SKILL", skillId)).build());
        row.setScore(score);
        row.setTier(tier);
        row.setRatingCount((int) ratingCount);
        row.setRatingAvg(bayes);
        row.setDownloadCount((int) downloads);
        row.setUpdatedAt(Instant.now());
        row.setSignals(Map.of(
                "ratingCount", ratingCount,
                "ratingAvgRaw", avg != null ? avg.doubleValue() : 0.0,
                "globalMean", m,
                "downloads", downloads));
        ReputationScore saved = scoreRepository.save(row);
        // Contributor tier derives from the author's item scores (BA §9.3),
        // so a skill-score change refreshes the author too.
        if (skill.getCreatedBy() != null) {
            refreshContributor(skill.getCreatedBy());
        }
        return saved;
    }

    @Override
    @Transactional
    public ReputationScore refreshMcp(Long mcpId) {
        McpMaster mcp = mcpMasterRepository.findById(mcpId).orElse(null);
        if (mcp == null || "Y".equals(mcp.getDeletedYn())) {
            return null;
        }
        // Q2 (BA §14, chốt 17/11) — same as Skill: the creator's own rating
        // is stored but excluded from the score.
        Long creatorId = mcp.getCreatedBy();
        long ratingCount = creatorId == null
                ? mcpRatingRepository.countByMcpId(mcpId)
                : mcpRatingRepository.countByMcpIdAndUserIdNot(mcpId, creatorId);
        BigDecimal avg = creatorId == null
                ? mcpRatingRepository.averageRatingByMcpId(mcpId)
                : mcpRatingRepository.averageRatingByMcpIdExcludingUser(mcpId, creatorId);
        BigDecimal globalMean = mcpRatingRepository.averageRating();
        double m = globalMean != null ? globalMean.doubleValue() : DEFAULT_GLOBAL_MEAN;
        long downloads = mcpDownloadLogRepository.countByMcpIdAndUserIdNot(mcpId, mcp.getCreatedBy());

        BigDecimal bayes = null;
        BigDecimal score = null;
        String tier = "NEW";
        if (ratingCount >= MIN_RATINGS && avg != null) {
            double bayesValue = (BAYESIAN_PRIOR * m + avg.doubleValue() * ratingCount)
                    / (BAYESIAN_PRIOR + ratingCount);
            bayes = BigDecimal.valueOf(bayesValue).setScale(2, RoundingMode.HALF_UP);
            double quality = bayesValue / 5.0;
            double popularity = Math.min(Math.log10(downloads + 1) / 2.0, 1.0);
            double scoreValue = (0.8 * quality + 0.2 * popularity) * 100.0;
            score = BigDecimal.valueOf(scoreValue).setScale(2, RoundingMode.HALF_UP);
            if (scoreValue >= POPULAR_THRESHOLD && downloads >= POPULAR_MIN_DOWNLOADS) {
                tier = "POPULAR";
            } else if (scoreValue >= TRUSTED_THRESHOLD) {
                tier = "TRUSTED";
            }
            // n >= 5 but score < 70: tier stays NEW and the (non-null) score is
            // shown, so the user sees "not trusted yet" instead of hiding.
        }

        ReputationScore row = scoreRepository.findById(new ReputationScoreId("MCP", mcpId))
                .orElse(ReputationScore.builder()
                        .id(new ReputationScoreId("MCP", mcpId)).build());
        row.setScore(score);
        row.setTier(tier);
        row.setRatingCount((int) ratingCount);
        row.setRatingAvg(bayes);
        row.setDownloadCount((int) downloads);
        row.setUpdatedAt(Instant.now());
        row.setSignals(Map.of(
                "ratingCount", ratingCount,
                "ratingAvgRaw", avg != null ? avg.doubleValue() : 0.0,
                "globalMean", m,
                "downloads", downloads));
        ReputationScore saved = scoreRepository.save(row);
        // Contributor tier derives from the author's item scores (BA §9.3),
        // so an MCP-score change refreshes the author too.
        if (mcp.getCreatedBy() != null) {
            refreshContributor(mcp.getCreatedBy());
        }
        return saved;
    }

    @Override
    @Transactional
    public ReputationScore refreshAiTool(Long toolId) {
        AiTool tool = aiToolRepository.findById(toolId).orElse(null);
        if (tool == null || AiTool.DELETED.equals(tool.getDeletedYn())) {
            return null;
        }
        // Q1 (BA §14, chốt 17/11): AI Tools have NO star ratings — the score
        // is download popularity only:
        //   popularity = min(log10(downloads + 1) / 2, 1)
        //   score      = popularity * 100
        // Q2 in spirit: the uploader's own downloads do not count.
        // Worked examples: 5 downloads → 38.91 (NEW, below 70);
        // 25 downloads → 70.75 (TRUSTED); 50 downloads → 85.38 (POPULAR).
        long downloads = tool.getCreatedBy() == null
                ? aiToolDownloadLogRepository.countByToolId(toolId)
                : aiToolDownloadLogRepository.countByToolIdAndUserIdNot(toolId, tool.getCreatedBy());

        BigDecimal score = null;
        String tier = "NEW";
        if (downloads >= MIN_DOWNLOADS) {
            double popularity = Math.min(Math.log10(downloads + 1) / 2.0, 1.0);
            double scoreValue = popularity * 100.0;
            score = BigDecimal.valueOf(scoreValue).setScale(2, RoundingMode.HALF_UP);
            if (scoreValue >= POPULAR_THRESHOLD && downloads >= POPULAR_MIN_DOWNLOADS) {
                tier = "POPULAR";
            } else if (scoreValue >= TRUSTED_THRESHOLD) {
                tier = "TRUSTED";
            }
            // downloads >= 5 but score < 70: tier stays NEW and the (non-null)
            // score is shown, so the user sees "not trusted yet".
        }

        ReputationScore row = scoreRepository.findById(new ReputationScoreId("AI_TOOL", toolId))
                .orElse(ReputationScore.builder()
                        .id(new ReputationScoreId("AI_TOOL", toolId)).build());
        row.setScore(score);
        row.setTier(tier);
        row.setRatingCount(0);
        row.setRatingAvg(null);
        row.setDownloadCount((int) downloads);
        row.setSuccessRate(null);
        row.setUpdatedAt(Instant.now());
        row.setSignals(Map.of("downloads", downloads));
        // No contributor refresh: tool authors are counted via their
        // Skill + MCP items (BA §9.3); tools carry no author reputation.
        return scoreRepository.save(row);
    }

    @Override
    @Transactional
    public ReputationScore refreshAiNews(Long newsId) {
        AiNews news = aiNewsRepository.findById(newsId).orElse(null);
        if (news == null || "Y".equals(news.getArchiveYn())) {
            return null;
        }
        // AI News is batch-imported from email — it has no author user, so
        // there is no self-like / self-view to exclude (unlike Q2 for
        // Skill/MCP). Likes and views are both per-user unique
        // (ai_news_like PK, ai_news_view_log unique), so the ratio is a
        // like rate over distinct viewers.
        long likes = aiNewsLikeRepository.countByNewsIdNative(newsId);
        long views = aiNewsViewLogRepository.countByNewsId(newsId);
        long commentVotes = aiNewsCommentVoteRepository.countVotesByNewsId(newsId);
        long ageDays = Math.max(0, Duration.between(news.getCreatedAt(), Instant.now()).toDays());
        double wilson = wilsonLowerBound(likes, views);
        // score = wilson * 100 * 0.5^(ageDays / 7) — 7-day half-life (BA §9.1,
        // Q4 defaults). Comment votes enter the explain breakdown only,
        // NOT the score.
        // Worked examples (age 0): 10 likes / 10 views → 72.25 (TRUSTED);
        // 5/5 → 56.55 (NEW, below 70); 20/20 → 83.89 (TRUSTED);
        // 30/30 → 88.65 (POPULAR); same 10/10 at age 7d → 36.12 (NEW).
        BigDecimal score = null;
        String tier = "NEW";
        if (views >= MIN_VIEWS) {
            double decayed = wilson * 100.0 * Math.pow(0.5, ageDays / (double) NEWS_HALF_LIFE_DAYS);
            score = BigDecimal.valueOf(decayed).setScale(2, RoundingMode.HALF_UP);
            if (decayed >= POPULAR_THRESHOLD) {
                tier = "POPULAR";
            } else if (decayed >= TRUSTED_THRESHOLD) {
                tier = "TRUSTED";
            }
            // views >= 5 but score < 70: tier stays NEW and the (non-null)
            // score is shown.
        }

        ReputationScore row = scoreRepository.findById(new ReputationScoreId("AI_NEWS", newsId))
                .orElse(ReputationScore.builder()
                        .id(new ReputationScoreId("AI_NEWS", newsId)).build());
        row.setScore(score);
        row.setTier(tier);
        // Column mapping for news (no stars, no downloads): ratingCount holds
        // the like count, downloadCount holds the distinct-viewer count, and
        // successRate holds the Wilson like/view lower bound (a rate, like
        // the install success rate it was designed for).
        row.setRatingCount((int) likes);
        row.setRatingAvg(null);
        row.setDownloadCount((int) views);
        row.setSuccessRate(BigDecimal.valueOf(wilson).setScale(4, RoundingMode.HALF_UP));
        row.setUpdatedAt(Instant.now());
        row.setSignals(Map.of(
                "likes", likes,
                "views", views,
                "commentVotes", commentVotes,
                "ageDays", ageDays,
                "wilson", wilson));
        // No contributor refresh: news items have no author user.
        return scoreRepository.save(row);
    }

    @Override
    @Transactional
    public ReputationScore refreshSkillRequest(Long requestId) {
        SkillRequest request = skillRequestRepository.findById(requestId).orElse(null);
        if (request == null) {
            return null;
        }
        // Lazy auto-confirm (Q8) is evaluated before scoring, so a request
        // whose 7-day window has passed scores as FULFILLED on the next
        // refresh (vote, propose/decision, or detail read).
        request = evaluateAutoConfirm(request);

        // Q6 (BA §14, chốt 18/11): only votes from verified users count.
        // "Verified" = ACTIVE + enabled account (no email-verified flag
        // exists on the user table — see SkillRequestVoteRepository).
        long verifiedVotes = skillRequestVoteRepository.countVerifiedByRequestId(requestId);
        long totalVotes = request.getVoteCount() == null ? 0 : request.getVoteCount();
        long ageDays = Math.max(0, Duration.between(request.getCreatedAt(), Instant.now()).toDays());
        boolean fulfilled = SkillRequest.FULFILLMENT_FULFILLED.equals(request.getFulfillmentStatus());
        // score = min(verifiedVotes, 50)/50 * 100 * 0.5^(ageDays/30),
        // +15 when FULFILLED (capped at 100). Requests live longer than
        // news, hence the 30-day half-life (vs 7 days for news).
        // Worked examples (age 0): 5 verified votes → 10.00 (NEW, below 70);
        // 40 verified votes → 80.00 (TRUSTED); 40 votes + FULFILLED → 95.00
        // (POPULAR); 50 votes at age 30d → 50.00 (NEW).
        BigDecimal score = null;
        String tier = "NEW";
        if (verifiedVotes >= MIN_VERIFIED_VOTES) {
            double base = Math.min(verifiedVotes, 50) / 50.0 * 100.0;
            double decayed = base * Math.pow(0.5, ageDays / (double) REQUEST_HALF_LIFE_DAYS);
            double scoreValue = fulfilled ? Math.min(100.0, decayed + FULFILLED_BOOST) : decayed;
            score = BigDecimal.valueOf(scoreValue).setScale(2, RoundingMode.HALF_UP);
            if (scoreValue >= POPULAR_THRESHOLD) {
                tier = "POPULAR";
            } else if (scoreValue >= TRUSTED_THRESHOLD) {
                tier = "TRUSTED";
            }
            // verifiedVotes >= 5 but score < 70: tier stays NEW and the
            // (non-null) score is shown.
        }

        ReputationScore row = scoreRepository.findById(new ReputationScoreId("SKILL_REQUEST", requestId))
                .orElse(ReputationScore.builder()
                        .id(new ReputationScoreId("SKILL_REQUEST", requestId)).build());
        row.setScore(score);
        row.setTier(tier);
        // Column mapping for requests (no stars, no downloads): ratingCount
        // holds the verified vote count, downloadCount holds the total vote
        // count (same reuse pattern as AI News).
        row.setRatingCount((int) verifiedVotes);
        row.setRatingAvg(null);
        row.setDownloadCount((int) totalVotes);
        row.setSuccessRate(null);
        row.setUpdatedAt(Instant.now());
        row.setSignals(Map.of(
                "verifiedVotes", verifiedVotes,
                "totalVotes", totalVotes,
                "ageDays", ageDays,
                "fulfilled", fulfilled,
                "fulfilledSkillId", request.getFulfilledSkillId() == null
                        ? 0 : request.getFulfilledSkillId()));
        ReputationScore saved = scoreRepository.save(row);
        // Fulfilled count feeds the contributor tier (BA §9.3): refresh the
        // author of the fulfilling skill when the request is fulfilled.
        if (fulfilled && request.getFulfilledSkillId() != null) {
            Skill skill = skillRepository.findById(request.getFulfilledSkillId()).orElse(null);
            if (skill != null && skill.getCreatedBy() != null) {
                refreshContributor(skill.getCreatedBy());
            }
        }
        return saved;
    }

    /**
     * Lazy auto-confirm (GĐ5, Q8 chốt 18/11): a PENDING_CONFIRM request whose
     * 7-day decision window has passed becomes FULFILLED if the linked skill
     * still meets the bar (APPROVED and not deleted); otherwise it reverts
     * to OPEN (link + deadline cleared) so it can be proposed again.
     * Runs on every refreshSkillRequest and on request detail read (which
     * calls refreshSkillRequest when the deadline has passed). A nightly
     * scheduled job for this is still pending (BA §9.5) — until then, a
     * request whose deadline passed but nobody refreshed it keeps its
     * PENDING_CONFIRM status in the feed.
     */
    private SkillRequest evaluateAutoConfirm(SkillRequest request) {
        if (!SkillRequest.FULFILLMENT_PENDING_CONFIRM.equals(request.getFulfillmentStatus())) {
            return request;
        }
        if (request.getFulfillmentDeadline() == null
                || Instant.now().isBefore(request.getFulfillmentDeadline())) {
            return request;
        }
        Skill skill = request.getFulfilledSkillId() == null ? null
                : skillRepository.findById(request.getFulfilledSkillId()).orElse(null);
        boolean stillQualifies = skill != null
                && Skill.STATUS_APPROVED.equals(skill.getApprovalStatus())
                && !"Y".equals(skill.getDeletedYn());
        if (stillQualifies) {
            request.setFulfillmentStatus(SkillRequest.FULFILLMENT_FULFILLED);
            request.setFulfilledAt(Instant.now());
        } else {
            request.setFulfillmentStatus(SkillRequest.FULFILLMENT_OPEN);
            request.setFulfilledSkillId(null);
            request.setFulfillmentDeadline(null);
            request.setFulfilledAt(null);
        }
        return skillRequestRepository.save(request);
    }

    /**
     * Wilson score lower bound (z = 1.96, 95%) for k successes out of n
     * trials — the small-sample-safe rate used for AI News like/view
     * (BA §9.1: "Wilson for rates"). 0 when there are no trials.
     */
    private static double wilsonLowerBound(long successes, long trials) {
        if (trials <= 0) {
            return 0.0;
        }
        double z = 1.96;
        double p = (double) successes / trials;
        double denom = 1.0 + z * z / trials;
        double centre = p + z * z / (2.0 * trials);
        double margin = z * Math.sqrt((p * (1.0 - p) + z * z / (4.0 * trials)) / trials);
        return Math.max(0.0, (centre - margin) / denom);
    }

    @Override
    @Transactional
    public void refreshContributor(Long userId) {
        // Contributor tier derives from ALL of the author's items (BA §9.3):
        // Skills (GĐ2) + MCPs (GĐ3). Each module contributes its own cached scores.
        List<Skill> skillItems = skillRepository.findByCreatedBy(userId).stream()
                .filter(s -> !"Y".equals(s.getDeletedYn())).toList();
        List<McpMaster> mcpItems = mcpMasterRepository.findByCreatedBy(userId).stream()
                .filter(m -> !"Y".equals(m.getDeletedYn())).toList();
        int itemCount = skillItems.size() + mcpItems.size();
        List<ReputationScore> scores = new ArrayList<>();
        if (!skillItems.isEmpty()) {
            scores.addAll(scoreRepository.findByIdTargetTypeAndIdTargetIdIn("SKILL",
                    skillItems.stream().map(Skill::getId).toList()));
        }
        if (!mcpItems.isEmpty()) {
            scores.addAll(scoreRepository.findByIdTargetTypeAndIdTargetIdIn("MCP",
                    mcpItems.stream().map(McpMaster::getId).toList()));
        }
        double avgScore = scores.stream()
                .filter(s -> s.getScore() != null)
                .mapToDouble(s -> s.getScore().doubleValue())
                .average().orElse(0.0);
        // Fulfilled requests (GĐ5, Q8): requests whose fulfilling skill was
        // created by this user — counted from skill_requests directly (the
        // fulfillment link), not from comments.
        int fulfilled = 0;
        if (!skillItems.isEmpty()) {
            fulfilled = (int) skillRequestRepository.countByFulfilledSkillIdInAndFulfillmentStatus(
                    skillItems.stream().map(Skill::getId).toList(),
                    SkillRequest.FULFILLMENT_FULFILLED);
        }

        // Tier rules (BA §9.3, extended GĐ5 with fulfilled requests):
        // NEW_MEMBER: no items and no fulfilled requests.
        // VERIFIED: >= 1 item OR >= 1 fulfilled request.
        // TRUSTED_AUTHOR: (>= 3 items OR >= 3 fulfilled) AND avg >= 70.
        // EXPERT: (>= 5 items OR >= 5 fulfilled) AND avg >= 85.
        // The average-score gate applies to every path: fulfilling requests
        // alone never promotes an author whose items score poorly.
        String tier = "NEW_MEMBER";
        if (itemCount > 0 || fulfilled > 0) {
            tier = "VERIFIED";
        }
        if ((itemCount >= 3 || fulfilled >= 3) && avgScore >= TRUSTED_THRESHOLD) {
            tier = "TRUSTED_AUTHOR";
        }
        if ((itemCount >= 5 || fulfilled >= 5) && avgScore >= POPULAR_THRESHOLD) {
            tier = "EXPERT";
        }

        ContributorReputation row = contributorRepository.findById(userId)
                .orElse(ContributorReputation.builder().userId(userId).build());
        row.setTier(tier);
        row.setItemCount(itemCount);
        row.setAvgScore(scores.isEmpty() ? null
                : BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP));
        row.setFulfilledRequests(fulfilled);
        row.setUpdatedAt(Instant.now());
        contributorRepository.save(row);
    }

    @Override
    @Transactional(readOnly = true)
    public ContributorResponse getContributor(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        ContributorReputation row = contributorRepository.findById(userId).orElse(null);
        if (row == null) {
            return new ContributorResponse(userId,
                    user == null ? null : user.getName(),
                    user == null ? null : user.getAvatarUrl(),
                    "NEW_MEMBER", 0, null, 0, 0);
        }
        return new ContributorResponse(userId,
                user == null ? null : user.getName(),
                user == null ? null : user.getAvatarUrl(),
                row.getTier(), row.getItemCount(), row.getAvgScore(),
                row.getFulfilledRequests(), row.getReportCount());
    }

    @Override
    @Transactional(readOnly = true)
    public ReputationResponse getReputation(String targetType, Long targetId) {
        ReputationScore row = scoreRepository.findById(
                new ReputationScoreId(targetType.toUpperCase(), targetId)).orElse(null);
        if (row == null) {
            return new ReputationResponse(targetType.toUpperCase(), targetId,
                    null, "NEW", 0, null, 0, null);
        }
        return toResponse(row);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ReputationScore> getReputations(String targetType, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }
        return scoreRepository.findByIdTargetTypeAndIdTargetIdIn(targetType, targetIds)
                .stream().collect(Collectors.toMap(s -> s.getId().getTargetId(), Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public ReputationExplainResponse explain(String targetType, Long targetId) {
        String type = targetType.toUpperCase();
        ReputationScore row = scoreRepository.findById(
                new ReputationScoreId(type, targetId)).orElse(null);
        List<String> reasons = new ArrayList<>();
        switch (type) {
            case "AI_TOOL" -> explainAiTool(row, reasons);
            case "AI_NEWS" -> explainAiNews(row, reasons);
            case "SKILL_REQUEST" -> explainSkillRequest(row, reasons);
            default -> explainRated(row, reasons);
        }
        BigDecimal score = row == null ? null : row.getScore();
        String tier = row == null ? "NEW" : row.getTier();
        return new ReputationExplainResponse(type, targetId, score, tier, reasons);
    }

    /**
     * Reasons for a star-rated item (Skill / MCP). Part C fix (GĐ4): the
     * "most used" reason now keys off tier == POPULAR, not score ≥ 85 —
     * a TRUSTED item at 86 points with fewer than 20 downloads previously
     * claimed to be "most used".
     */
    private void explainRated(ReputationScore row, List<String> reasons) {
        if (row == null || row.getRatingCount() < MIN_RATINGS) {
            int have = row == null ? 0 : row.getRatingCount();
            reasons.add("Mới: cần thêm " + (MIN_RATINGS - have)
                    + " lượt đánh giá nữa để có hạng (hiện có " + have + "/" + MIN_RATINGS + ").");
        } else {
            reasons.add("Điểm Bayesian " + row.getRatingAvg() + "/5 từ "
                    + row.getRatingCount() + " lượt đánh giá.");
            reasons.add(row.getDownloadCount() + " lượt tải.");
            addTierReason(row, reasons);
        }
    }

    /** Reasons for an AI Tool: no stars (Q1) — the score is download-based. */
    private void explainAiTool(ReputationScore row, List<String> reasons) {
        int downloads = row == null ? 0 : row.getDownloadCount();
        if (row == null || downloads < MIN_DOWNLOADS) {
            reasons.add("Mới: cần thêm " + (MIN_DOWNLOADS - downloads)
                    + " lượt tải nữa để có hạng (hiện có " + downloads + "/" + MIN_DOWNLOADS + ").");
            reasons.add("Tool không có đánh giá sao — điểm uy tín tính từ lượt tải.");
        } else {
            reasons.add(downloads + " lượt tải — điểm uy tín tính từ lượt tải (tool không có đánh giá sao).");
            addTierReason(row, reasons);
        }
    }

    /** Reasons for a skill request: verified votes + age decay + fulfillment. */
    private void explainSkillRequest(ReputationScore row, List<String> reasons) {
        long verifiedVotes = row == null ? 0 : row.getRatingCount();
        long totalVotes = row == null ? 0 : row.getDownloadCount();
        if (row == null || verifiedVotes < MIN_VERIFIED_VOTES) {
            reasons.add("Mới: cần thêm " + (MIN_VERIFIED_VOTES - verifiedVotes)
                    + " lượt bình chọn nữa để có hạng (hiện có " + verifiedVotes
                    + "/" + MIN_VERIFIED_VOTES + ").");
            reasons.add("Chỉ tính lượt bình chọn của tài khoản đang hoạt động.");
        } else {
            reasons.add(verifiedVotes + " lượt bình chọn từ tài khoản đang hoạt động"
                    + (totalVotes > verifiedVotes ? " (" + totalVotes + " lượt tất cả)." : "."));
            long ageDays = signalLong(row, "ageDays");
            if (ageDays >= REQUEST_HALF_LIFE_DAYS) {
                reasons.add("Đăng cách đây " + ageDays + " ngày — điểm giảm dần theo thời gian.");
            }
            if (signalBoolean(row, "fulfilled")) {
                reasons.add("Đã được đáp ứng (+15 điểm).");
            }
            addTierReason(row, reasons);
        }
    }

    /** Reasons for an AI News item: like/view ratio (Wilson) + time decay. */
    private void explainAiNews(ReputationScore row, List<String> reasons) {
        int likes = row == null ? 0 : row.getRatingCount();
        int views = row == null ? 0 : row.getDownloadCount();
        if (row == null || views < MIN_VIEWS) {
            reasons.add("Mới: cần thêm " + (MIN_VIEWS - views)
                    + " lượt xem nữa để có hạng (hiện có " + views + "/" + MIN_VIEWS + ").");
        } else {
            double ratio = views > 0 ? 100.0 * likes / views : 0.0;
            reasons.add(likes + " lượt thích trên " + views + " lượt xem (tỉ lệ thích "
                    + String.format(Locale.ROOT, "%.0f", ratio) + "%).");
            long commentVotes = signalLong(row, "commentVotes");
            if (commentVotes > 0) {
                reasons.add(commentVotes + " lượt bình chọn hữu ích cho bình luận.");
            }
            long ageDays = signalLong(row, "ageDays");
            if (ageDays >= NEWS_HALF_LIFE_DAYS) {
                reasons.add("Tin đăng cách đây " + ageDays + " ngày — điểm giảm dần theo thời gian.");
            }
            addTierReason(row, reasons);
        }
    }

    /** Shared tier verdict line, keyed off the stored tier (never the raw score). */
    private void addTierReason(ReputationScore row, List<String> reasons) {
        if ("POPULAR".equals(row.getTier())) {
            reasons.add("Thuộc nhóm được dùng nhiều nhất (≥" + POPULAR_THRESHOLD + " điểm).");
        } else if ("TRUSTED".equals(row.getTier())) {
            reasons.add("Đạt ngưỡng Đáng tin (≥" + (int) TRUSTED_THRESHOLD + " điểm).");
        } else {
            reasons.add("Điểm chưa đạt ngưỡng Đáng tin (" + (int) TRUSTED_THRESHOLD + ").");
        }
    }

    private static long signalLong(ReputationScore row, String key) {
        if (row == null || row.getSignals() == null) {
            return 0;
        }
        Object value = row.getSignals().get(key);
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static boolean signalBoolean(ReputationScore row, String key) {
        if (row == null || row.getSignals() == null) {
            return false;
        }
        Object value = row.getSignals().get(key);
        return value instanceof Boolean b && b;
    }

    private ReputationResponse toResponse(ReputationScore row) {
        return new ReputationResponse(row.targetType(), row.targetId(), row.getScore(),
                row.getTier(), row.getRatingCount(), row.getRatingAvg(),
                row.getDownloadCount(), row.getSuccessRate());
    }
}
