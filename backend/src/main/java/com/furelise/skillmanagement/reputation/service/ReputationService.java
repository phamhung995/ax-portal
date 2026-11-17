package com.furelise.skillmanagement.reputation.service;

import com.furelise.skillmanagement.reputation.dto.ContributorResponse;
import com.furelise.skillmanagement.reputation.dto.ReputationExplainResponse;
import com.furelise.skillmanagement.reputation.dto.ReputationResponse;
import com.furelise.skillmanagement.reputation.model.ReputationScore;

import java.util.List;
import java.util.Map;

/**
 * Reputation core: one cached score per (target_type, target_id).
 * Scoring (guide §2): Bayesian average for stars (prior C=10, global mean m),
 * Wilson for success rates, minimum-sample thresholds, tier NEW below sample.
 */
public interface ReputationService {

    /** Minimum number of ratings before a score is shown (tier NEW below it). */
    int MIN_RATINGS = 5;

    /** Minimum number of downloads before an AI Tool score is shown. */
    int MIN_DOWNLOADS = 5;

    /** Minimum number of distinct views before an AI News score is shown. */
    int MIN_VIEWS = 5;

    /** Half-life (days) of the AI News time decay (BA §9.1, Q4 defaults). */
    int NEWS_HALF_LIFE_DAYS = 7;

    /** Bayesian prior weight (a "virtual" 10 ratings at the global mean). */
    int BAYESIAN_PRIOR = 10;

    /** Minimum number of verified votes before a request score is shown. */
    int MIN_VERIFIED_VOTES = 5;

    /** Half-life (days) of the Skill Request time decay (GĐ5). */
    int REQUEST_HALF_LIFE_DAYS = 30;

    /** Display boost for a FULFILLED request, capped at 100 (GĐ5). */
    int FULFILLED_BOOST = 15;

    /** Requester decision window for a proposed fulfillment (GĐ5, Q8). */
    int FULFILLMENT_DECISION_DAYS = 7;

    /** Recompute and persist the score of one skill. */
    ReputationScore refreshSkill(Long skillId);

    /** Recompute and persist the score of one MCP (same formula as Skill, GĐ3). */
    ReputationScore refreshMcp(Long mcpId);

    /**
     * Recompute and persist the score of one AI Tool (GĐ4). Q1 (BA §14):
     * tools have NO star ratings — the score is download popularity only.
     */
    ReputationScore refreshAiTool(Long toolId);

    /**
     * Recompute and persist the score of one AI News item (GĐ4): Wilson
     * lower bound on the like/view ratio, decayed with a 7-day half-life.
     */
    ReputationScore refreshAiNews(Long newsId);

    /**
     * Recompute and persist the score of one skill request (GĐ5): verified
     * votes only (Q6), 30-day half-life decay, +15 display boost when
     * FULFILLED (capped at 100). Also evaluates the lazy auto-confirm
     * (PENDING_CONFIRM past its deadline with the linked skill still
     * APPROVED → FULFILLED) before computing.
     */
    ReputationScore refreshSkillRequest(Long requestId);

    /** Recompute and persist the contributor tier of one user. */
    void refreshContributor(Long userId);

    /** Cached contributor row (NEW_MEMBER / empty row when never computed). */
    ContributorResponse getContributor(Long userId);

    /** Cached row for one target (NEW / empty row when never computed). */
    ReputationResponse getReputation(String targetType, Long targetId);

    /** Cached rows for a set of targets of one type. */
    Map<Long, ReputationScore> getReputations(String targetType, List<Long> targetIds);

    /** Human-readable reasons behind a score. */
    ReputationExplainResponse explain(String targetType, Long targetId);
}
