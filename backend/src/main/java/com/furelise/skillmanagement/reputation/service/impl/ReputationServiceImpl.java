package com.furelise.skillmanagement.reputation.service.impl;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
        return scoreRepository.save(row);
    }

    @Override
    @Transactional
    public void refreshContributor(Long userId) {
        List<Skill> items = skillRepository.findByCreatedBy(userId).stream()
                .filter(s -> !"Y".equals(s.getDeletedYn())).toList();
        List<ReputationScore> scores = items.isEmpty() ? List.of()
                : scoreRepository.findByIdTargetTypeAndIdTargetIdIn("SKILL",
                        items.stream().map(Skill::getId).toList());
        double avgScore = scores.stream()
                .filter(s -> s.getScore() != null)
                .mapToDouble(s -> s.getScore().doubleValue())
                .average().orElse(0.0);
        // Fulfilled requests: 0 until Q8 (BA §14) defines FULFILLED — the
        // bounty-based count was dropped with the bounty phase (giai đoạn sau).
        int fulfilled = 0;

        String tier = "NEW_MEMBER";
        if (!items.isEmpty()) {
            tier = "VERIFIED";
        }
        if (items.size() >= 3 && avgScore >= TRUSTED_THRESHOLD) {
            tier = "TRUSTED_AUTHOR";
        }
        if (items.size() >= 5 && avgScore >= POPULAR_THRESHOLD) {
            tier = "EXPERT";
        }

        ContributorReputation row = contributorRepository.findById(userId)
                .orElse(ContributorReputation.builder().userId(userId).build());
        row.setTier(tier);
        row.setItemCount(items.size());
        row.setAvgScore(scores.isEmpty() ? null
                : BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP));
        row.setFulfilledRequests(fulfilled);
        row.setUpdatedAt(Instant.now());
        contributorRepository.save(row);
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
        ReputationScore row = scoreRepository.findById(
                new ReputationScoreId(targetType.toUpperCase(), targetId)).orElse(null);
        List<String> reasons = new ArrayList<>();
        if (row == null || row.getRatingCount() < MIN_RATINGS) {
            int have = row == null ? 0 : row.getRatingCount();
            reasons.add("Mới: cần thêm " + (MIN_RATINGS - have)
                    + " lượt đánh giá nữa để có hạng (hiện có " + have + "/" + MIN_RATINGS + ").");
        } else {
            reasons.add("Điểm Bayesian " + row.getRatingAvg() + "/5 từ "
                    + row.getRatingCount() + " lượt đánh giá.");
            reasons.add(row.getDownloadCount() + " lượt tải.");
            if (row.getScore() != null && row.getScore().doubleValue() >= POPULAR_THRESHOLD) {
                reasons.add("Thuộc nhóm được dùng nhiều nhất (≥" + POPULAR_THRESHOLD + " điểm).");
            } else if (row.getScore() != null && row.getScore().doubleValue() >= TRUSTED_THRESHOLD) {
                reasons.add("Đạt ngưỡng Đáng tin (≥" + (int) TRUSTED_THRESHOLD + " điểm).");
            } else {
                reasons.add("Điểm chưa đạt ngưỡng Đáng tin (" + (int) TRUSTED_THRESHOLD + ").");
            }
        }
        BigDecimal score = row == null ? null : row.getScore();
        String tier = row == null ? "NEW" : row.getTier();
        return new ReputationExplainResponse(targetType.toUpperCase(), targetId, score, tier, reasons);
    }

    private ReputationResponse toResponse(ReputationScore row) {
        return new ReputationResponse(row.targetType(), row.targetId(), row.getScore(),
                row.getTier(), row.getRatingCount(), row.getRatingAvg(),
                row.getDownloadCount(), row.getSuccessRate());
    }
}
