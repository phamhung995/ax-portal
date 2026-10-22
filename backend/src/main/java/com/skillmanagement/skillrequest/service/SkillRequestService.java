package com.skillmanagement.skillrequest.service;

import com.skillmanagement.skillrequest.dto.SkillRequestCommentPage;
import com.skillmanagement.skillrequest.dto.SkillRequestCreateRequest;
import com.skillmanagement.skillrequest.dto.SkillRequestFeedItem;
import com.skillmanagement.skillrequest.dto.SkillRequestFeedResult;
import com.skillmanagement.skillrequest.dto.SkillRequestSuggestions;
import com.skillmanagement.skillrequest.dto.SkillRequestStatsResult;
import com.skillmanagement.skillrequest.model.SkillRequestFile;
import com.skillmanagement.user.model.User;
import org.springframework.web.multipart.MultipartFile;

/**
 * Skill Request Management (feed) operations: create, feed (ranked,
 * cursor-paged), vote/unvote, comments (with skill link + resolve),
 * duplicate suggestions.
 */
public interface SkillRequestService {

    /**
     * One feed page of requests ranked by
     * {@code (voteCount*2 + commentCount) * timeDecay}, then createdAt DESC.
     *
     * @param cursor opaque cursor from the previous page (null = first page)
     * @param limit  page size
     * @param mine   only requests created by the viewer
     * @param title  optional title filter (case-insensitive contains)
     */
    SkillRequestFeedResult feed(String cursor, int limit, boolean mine, String title, User viewer);

    /**
     * Detail of one request (same shape as a feed item).
     * 404 when the request does not exist.
     */
    SkillRequestFeedItem detail(Long requestId, User viewer);

    /** Creates a public request (posted to the feed immediately), with optional file attachments. */
    Long create(SkillRequestCreateRequest request, MultipartFile[] files, User creator);

    /** Votes for a request (409 when already voted). */
    void vote(Long requestId, User user);

    /** Removes the viewer's vote (404 when not voted). */
    void unvote(Long requestId, User user);

    /**
     * Paged top-level comments (newest first, non-deleted), each with its
     * reply tree nested (max 3 levels; deeper replies attach to level 3).
     */
    SkillRequestCommentPage listComments(Long requestId, User viewer, int page, int size);

    /** Adds a comment (any authenticated user), optionally linked to a skill and/or a parent comment. */
    void addComment(Long requestId, User user, String content, Long linkedSkillId, Long parentId);

    /** Likes a comment (409 when already liked). */
    void likeComment(Long requestId, Long commentId, User user);

    /** Removes the viewer's like (404 when not liked). */
    void unlikeComment(Long requestId, Long commentId, User user);

    /** Soft-deletes a comment (comment creator or admin). */
    void deleteComment(Long requestId, Long commentId, User user);

    /**
     * Marks a skill-linked comment as resolved. Only the request creator or
     * an admin may resolve. Idempotent.
     */
    void resolveComment(Long requestId, Long commentId, User user);

    /**
     * Duplicate-detection suggestions (option A): skills and other requests
     * matching the keyword by title/description/hashtags.
     */
    SkillRequestSuggestions suggestions(String keyword, User viewer);

    /**
     * Sidebar stats: top-10 voted requests, top-10 requesters (by request
     * count, with resolved-comment count) and top-10 popular hashtags.
     */
    SkillRequestStatsResult stats();

    /** Returns the attachment metadata (404 when the file does not belong to the request). */
    SkillRequestFile getFile(Long requestId, Long fileId);
}
