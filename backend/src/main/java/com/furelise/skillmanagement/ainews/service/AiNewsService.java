package com.furelise.skillmanagement.ainews.service;

import com.furelise.skillmanagement.ainews.dto.AiNewsDetail;
import com.furelise.skillmanagement.ainews.dto.AiNewsItem;
import com.furelise.skillmanagement.ainews.dto.AiNewsSearchCriteria;
import com.furelise.skillmanagement.ainews.dto.MentionUserItem;
import com.furelise.skillmanagement.user.model.User;

import java.util.List;

/**
 * AI News screen + Home card data (AI_News_Screen_SRS.md §5, Home SRS §7.4).
 * Read-only news content + like + view tracking. Comment operations
 * (FR-16..FR-20) live in {@link AiNewsCommentService}.
 */
public interface AiNewsService {

    /** List of active news items (no body), newest first. */
    List<AiNewsItem> list(int limit);

    /**
     * Advanced-search list: active news items matching the (optional)
     * criteria, newest first. A criteria with no fields set returns the
     * same result as {@link #list(int)}.
     */
    List<AiNewsItem> search(AiNewsSearchCriteria criteria, int limit);

    /** Detail of one active news item (with body). 404 NEWS-201 when missing/archived. */
    AiNewsDetail detail(Long newsId, User viewer);

    /** Toggle the like on a news item (silent — no notification). */
    int toggleLike(Long newsId, User viewer);

    /**
     * Record a view of a news item (idempotent per user — the frontend fires
     * this after 30s of reading the detail). Returns the distinct-viewer
     * count for the item.
     */
    int recordView(Long newsId, User viewer);

    /**
     * @mention autocomplete: up to 10 enabled users matching the query
     * (case-insensitive name or email LIKE, name-asc order). Blank query →
     * the first 10 enabled users.
     */
    List<MentionUserItem> mentionUsers(String query);
}
