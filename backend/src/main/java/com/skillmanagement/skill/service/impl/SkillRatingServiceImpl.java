package com.skillmanagement.skill.service.impl;

import com.skillmanagement.skill.dto.SkillRatingResponse;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.model.SkillRating;
import com.skillmanagement.user.model.User;
import com.skillmanagement.skill.repository.SkillRatingRepository;
import com.skillmanagement.common.RateLimiter;
import com.skillmanagement.skill.service.SkillAccessHelper;
import com.skillmanagement.skill.service.SkillRatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Star rating implementation (Detail SRS §5.5).
 * One rating per user per skill; re-rating is an UPSERT.
 */
@Service
@RequiredArgsConstructor
public class SkillRatingServiceImpl implements SkillRatingService {

    private static final int RATING_MAX_PER_MINUTE = 10;
    private static final int RATING_WINDOW_SECONDS = 60;

    private final SkillRatingRepository skillRatingRepository;
    private final SkillAccessHelper skillAccessHelper;
    private final RateLimiter rateLimiter;

    @Override
    @Transactional(readOnly = true)
    public SkillRatingResponse getRating(Long skillId) {
        BigDecimal avg = skillRatingRepository.averageRatingBySkillId(skillId);
        long count = skillRatingRepository.countBySkillId(skillId);
        return new SkillRatingResponse(
                avg != null ? avg.setScale(1, RoundingMode.HALF_UP).doubleValue() : 0.0, count);
    }

    @Override
    @Transactional(readOnly = true)
    public Double getMyRating(Long skillId, Long userId) {
        return skillRatingRepository.findBySkillIdAndUserId(skillId, userId)
                .map(r -> r.getRating().setScale(1, RoundingMode.HALF_UP).doubleValue())
                .orElse(null);
    }

    @Override
    @Transactional
    public SkillRatingResponse upsertRating(Long skillId, User user, double rating) {
        if (!rateLimiter.allow("skill-rating:" + user.getId(),
                RATING_MAX_PER_MINUTE, RATING_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.SKILL_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        if (rating < 1.0 || rating > 5.0 || Math.round(rating * 2) / 2.0 != rating) {
            throw new BusinessException(BusinessErrorCode.SKILL_INVALID_RATING,
                    "Rating must be between 1 and 5 in steps of 0.5.");
        }
        Skill skill = skillAccessHelper.getVisible(skillId, user);
        if (!Skill.STATUS_APPROVED.equals(skill.getApprovalStatus())
                && !skill.isVersionUpdatePending()) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_APPROVED,
                    "Only published skills can be rated.");
        }

        SkillRating existing = skillRatingRepository
                .findBySkillIdAndUserId(skillId, user.getId())
                .orElseGet(() -> SkillRating.builder()
                        .skillId(skillId)
                        .userId(user.getId())
                        .build());
        existing.setRating(BigDecimal.valueOf(rating).setScale(1, RoundingMode.HALF_UP));
        skillRatingRepository.save(existing);

        return getRating(skillId);
    }
}
