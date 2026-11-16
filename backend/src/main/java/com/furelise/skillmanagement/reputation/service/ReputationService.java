package com.furelise.skillmanagement.reputation.service;

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

    /** Bayesian prior weight (a "virtual" 10 ratings at the global mean). */
    int BAYESIAN_PRIOR = 10;

    /** Recompute and persist the score of one skill. */
    ReputationScore refreshSkill(Long skillId);

    /** Recompute and persist the contributor tier of one user. */
    void refreshContributor(Long userId);

    /** Cached row for one target (NEW / empty row when never computed). */
    ReputationResponse getReputation(String targetType, Long targetId);

    /** Cached rows for a set of targets of one type. */
    Map<Long, ReputationScore> getReputations(String targetType, List<Long> targetIds);

    /** Human-readable reasons behind a score. */
    ReputationExplainResponse explain(String targetType, Long targetId);
}
