package com.furelise.skillmanagement.skill.service;

import com.furelise.skillmanagement.skill.dto.SkillCommentLikeResponse;
import com.furelise.skillmanagement.skill.dto.SkillCommentPage;
import com.furelise.skillmanagement.user.model.User;

/**
 * Comment operations (Detail SRS §5.6).
 * 2026-11-08: threaded (depth 2) + "Helpful" likes + edit.
 */
public interface SkillCommentService {

    /**
     * Paged top-level comments for a skill (oldest first, non-deleted),
     * each with its non-deleted replies nested (chronological).
     */
    SkillCommentPage list(Long skillId, User viewer, int page, int size);

    /** Adds a comment (or a reply when parentId != null) to a PUBLISHED skill. */
    void add(Long skillId, User user, String content, Long parentId);

    /** Soft-deletes a comment (own comment or admin). */
    void delete(Long skillId, Long commentId, User user);

    /** Toggles the viewer's "Helpful" like on a comment. */
    SkillCommentLikeResponse toggleLike(Long skillId, Long commentId, User user);

    /** Edits the content of the viewer's own comment. */
    void edit(Long skillId, Long commentId, User user, String content);
}
