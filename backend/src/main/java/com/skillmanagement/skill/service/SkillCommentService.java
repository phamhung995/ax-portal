package com.skillmanagement.skill.service;

import com.skillmanagement.skill.dto.SkillCommentPage;
import com.skillmanagement.user.model.User;

/**
 * Comment operations (Detail SRS §5.6).
 */
public interface SkillCommentService {

    /** Paged comments for a skill (newest first, non-deleted). */
    SkillCommentPage list(Long skillId, User viewer, int page, int size);

    /** Adds a comment to a PUBLISHED skill. */
    void add(Long skillId, User user, String content);

    /** Soft-deletes a comment (own comment or admin). */
    void delete(Long skillId, Long commentId, User user);
}
