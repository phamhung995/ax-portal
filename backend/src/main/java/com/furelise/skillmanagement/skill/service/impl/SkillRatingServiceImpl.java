package com.furelise.skillmanagement.skill.service.impl;

import com.furelise.skillmanagement.skill.dto.SkillRatingResponse;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.model.SkillRating;
import com.furelise.skillmanagement.skill.repository.SkillRatingRepository;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import com.furelise.skillmanagement.common.RateLimiter;
import com.furelise.skillmanagement.gamification.service.GamificationService;
import com.furelise.skillmanagement.reputation.service.ReputationService;
import com.furelise.skillmanagement.skill.service.SkillAccessHelper;
import com.furelise.skillmanagement.skill.service.SkillRatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Star rating implementation (Detail SRS §5.5).
 * One rating per user per skill; re-rating is an UPSERT.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SkillRatingServiceImpl implements SkillRatingService {

    private static final int RATING_MAX_PER_MINUTE = 10;
    private static final int RATING_WINDOW_SECONDS = 60;

    private final SkillRatingRepository skillRatingRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final EventNotificationService eventNotificationService;
    private final SkillAccessHelper skillAccessHelper;
    private final RateLimiter rateLimiter;
    private final ReputationService reputationService;
    private final GamificationService gamificationService;

    @Value("${app.base-url:}")
    private String appBaseUrl;

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

        // Reputation: a new rating moves the skill's cached score; the rater
        // earns +2 points (daily cap 10).
        reputationService.refreshSkill(skillId);
        gamificationService.awardPoints(user.getId(), GamificationService.POINTS_RATING,
                "RATING", "Đánh giá skill", "SKILL", skillId);

        notifyCreator(skill, user, existing.getRating());
        return getRating(skillId);
    }

    /** In-app + email notification to the skill creator (not the rater). */
    private void notifyCreator(Skill skill, User rater, BigDecimal rating) {
        Long creatorId = skill.getCreatedBy();
        if (creatorId == null || creatorId.equals(rater.getId())) {
            return;
        }
        User creator = userRepository.findById(creatorId).orElse(null);
        if (creator == null) {
            return;
        }
        UserSettings settings = userSettingsRepository.findById(creatorId).orElse(null);
        String title = "New rating on skill \"" + skill.getName() + "\"";
        String skillUrl = skillDetailUrl(skill.getId());
        String body = "\"" + rater.getName() + "\" rated your skill \""
                + skill.getName() + "\" " + rating.toPlainString() + "/5."
                + (skillUrl != null ? "\n\n" + skillUrl : "");
        eventNotificationService.notify(rater, creator, settings,
                new EventNotificationService.EventDetails(
                        title,
                        new EventNotificationService.EventContext(
                                String.valueOf(skill.getId()), null, null, null, null),
                        "[AX Portal] " + title, body));
    }

    /** Frontend URL that opens the skill detail popup (?detail={id}). */
    private String skillDetailUrl(Long skillId) {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return null;
        }
        return appBaseUrl + "/skills?detail=" + skillId;
    }
}
