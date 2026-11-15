package com.furelise.skillmanagement.ainews.service;

import com.furelise.skillmanagement.ainews.dto.AiNewsCommentItem;
import com.furelise.skillmanagement.ainews.dto.AiNewsCommentRequest;
import com.furelise.skillmanagement.user.model.User;

import java.util.List;

/**
 * AI News comments (FR-16..FR-20) — extracted from {@link AiNewsService}
 * so the news-content service (list/search/detail/view/like) and the
 * comment subsystem (CRUD + votes + @mention notifications) evolve
 * independently.
 */
public interface AiNewsCommentService {

    /** Comments of a news item (top-level DESC, replies ASC — flattened list). */
    List<AiNewsCommentItem> comments(Long newsId, User viewer);

    /** Create a comment or reply (depth <= 2, deeper replies flatten to root). */
    AiNewsCommentItem addComment(Long newsId, User viewer, AiNewsCommentRequest request);

    /** Soft-delete a comment (owner or admin). */
    void deleteComment(Long newsId, Long commentId, User viewer);

    /** Toggle the "helpful" vote on a comment (not on own comment). */
    int toggleHelpful(Long newsId, Long commentId, User viewer);
}
