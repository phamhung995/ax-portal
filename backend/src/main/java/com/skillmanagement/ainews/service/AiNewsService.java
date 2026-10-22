package com.skillmanagement.ainews.service;

import com.skillmanagement.ainews.dto.AiNewsCommentItem;
import com.skillmanagement.ainews.dto.AiNewsCommentRequest;
import com.skillmanagement.ainews.dto.AiNewsDetail;
import com.skillmanagement.ainews.dto.AiNewsItem;
import com.skillmanagement.user.model.User;

import java.util.List;

/**
 * AI News screen + Home card data (AI_News_Screen_SRS.md §5, Home SRS §7.4).
 * Read-only news content + interactive like/comment (FR-15..FR-20).
 */
public interface AiNewsService {

    /** List of active news items (no body), newest first. */
    List<AiNewsItem> list(int limit);

    /** Detail of one active news item (with body). 404 NEWS-201 when missing/archived. */
    AiNewsDetail detail(Long newsId, User viewer);

    /** Comments of a news item (top-level DESC, replies ASC — flattened list). */
    List<AiNewsCommentItem> comments(Long newsId, User viewer);

    /** Create a comment or reply (depth <= 2, deeper replies flatten to root). */
    AiNewsCommentItem addComment(Long newsId, User viewer, AiNewsCommentRequest request);

    /** Soft-delete a comment (owner or admin). */
    void deleteComment(Long newsId, Long commentId, User viewer);

    /** Toggle the "helpful" vote on a comment (not on own comment). */
    int toggleHelpful(Long newsId, Long commentId, User viewer);

    /** Toggle the like on a news item (silent — no notification). */
    int toggleLike(Long newsId, User viewer);
}
