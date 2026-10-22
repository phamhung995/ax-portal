package com.skillmanagement.skill.service;

import com.skillmanagement.skill.dto.SkillRatingResponse;
import com.skillmanagement.user.model.User;

/**
 * Star rating operations (Detail SRS §5.5).
 */
public interface SkillRatingService {

    /** Aggregate rating for a skill (average + count). */
    SkillRatingResponse getRating(Long skillId);

    /** The viewer's own rating for a skill, or null if not yet rated. */
    Double getMyRating(Long skillId, Long userId);

    /**
     * Upserts the viewer's rating (1.0-5.0, step 0.5).
     * The skill must be PUBLISHED.
     */
    SkillRatingResponse upsertRating(Long skillId, User user, double rating);
}
