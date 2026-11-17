package com.furelise.skillmanagement.skillrequest.service;

import com.furelise.skillmanagement.skillrequest.dto.MentionUserItem;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestCommentPage;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestCreateRequest;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestFeedItem;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestFeedResult;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestSuggestions;
import com.furelise.skillmanagement.skillrequest.dto.SkillRequestStatsResult;
import com.furelise.skillmanagement.skillrequest.model.SkillRequestFile;
import com.furelise.skillmanagement.user.model.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Skill Request Management (feed) operations: create, feed (ranked,
 * cursor-paged), vote/unvote, comments (with skill link + resolve),
 * duplicate suggestions.
 */
public interface SkillRequestService {

    /**
     * One feed page of requests ranked by
     * {@code (voteCount*2 + commentCount) * timeDecay}, then createdAt DESC —
     * or, when sortBy is "reputation", by cached reputation score DESC
     * (nulls last, tie-break createdAt DESC, id DESC).
     *
     * @param cursor opaque cursor from the previous page (null = first page)
     * @param limit  page size
     * @param mine   only requests created by the viewer
     * @param title  optional title filter (case-insensitive contains)
     * @param sortBy "reputation" for best-first, anything else = default order
     */
    SkillRequestFeedResult feed(String cursor, int limit, boolean mine, String title,
                               String sortBy, User viewer);

    /**
     * The {@code limit} most recently created requests (createdAt DESC, id DESC),
     * same item shape as the feed — used by the Home "New Request" card.
     */
    List<SkillRequestFeedItem> recent(int limit, User viewer);

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
     * Proposes an APPROVED skill as the answer to a request (GĐ5, Q8).
     * Sets PENDING_CONFIRM with a 7-day decision deadline for the requester.
     * Rejects: skill not APPROVED, request already PENDING_CONFIRM/FULFILLED,
     * or the proposer IS the requester (the only ban carried over from the
     * bounty design — proposing your own skill for someone else's request
     * is legitimate).
     */
    void proposeFulfillment(Long requestId, Long skillId, User user);

    /**
     * Requester decision on a pending fulfillment (GĐ5, Q8). accept=true →
     * FULFILLED; accept=false → back to OPEN (link + deadline cleared, the
     * request can be proposed again). Requester only.
     */
    void fulfillmentDecision(Long requestId, boolean accept, User user);

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
     * Updates a comment's content (comment creator only). The linked skill
     * and resolved state are preserved.
     */
    void updateComment(Long requestId, Long commentId, String content, User user);

    /**
     * @mention autocomplete: up to 10 enabled users matching the query
     * (case-insensitive name/email contains). Same behavior as AI News.
     */
    List<MentionUserItem> mentionUsers(String query);

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

    /**
     * Updates a skill request (title, description, hashtags, optional new files,
     * optional existing files to remove). Only the request creator may update.
     * Returns the updated request id.
     */
    Long update(Long requestId, SkillRequestCreateRequest request, MultipartFile[] files,
                List<Long> removeFileIds, User user);

    /**
     * Hard-deletes a skill request and all related data (votes, comments,
     * comment likes, file rows + physical files). Only the request creator
     * may delete.
     */
    void delete(Long requestId, User user);
}
