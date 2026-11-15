package com.furelise.skillmanagement.ainews.service.impl;

import com.furelise.skillmanagement.ainews.dto.AiNewsCommentItem;
import com.furelise.skillmanagement.ainews.dto.AiNewsCommentRequest;
import com.furelise.skillmanagement.ainews.email.CommentHtmlSanitizer;
import com.furelise.skillmanagement.ainews.model.AiNews;
import com.furelise.skillmanagement.ainews.model.AiNewsComment;
import com.furelise.skillmanagement.ainews.model.AiNewsCommentVote;
import com.furelise.skillmanagement.ainews.repository.AiNewsCommentRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsCommentVoteRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsRepository;
import com.furelise.skillmanagement.ainews.service.AiNewsCommentService;
import com.furelise.skillmanagement.common.RateLimiter;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI News comments (FR-16..FR-20) — comment CRUD, "helpful" votes, and the
 * @mention notification fan-out. Extracted from {@link AiNewsServiceImpl}
 * (the news-content service) to keep each class focused.
 */
@Service
@Slf4j
public class AiNewsCommentServiceImpl implements AiNewsCommentService {

    private static final int MAX_COMMENT_LENGTH = 1000;
    /**
     * Cap on the sanitized HTML length. The plain-text limit (1000) does not
     * bound the stored HTML — embedded base64 images or deeply nested markup
     * could make the row arbitrarily large (DB bloat + every comment-list
     * response). 20 KB of sanitized HTML is far beyond any real comment.
     */
    private static final int MAX_COMMENT_HTML_LENGTH = 20_000;
    /**
     * Cap on the number of top-level comment threads returned per news item.
     * The list is unbounded by design (no pagination in v1.0) but a
     * pathological item with thousands of threads would produce a
     * tens-of-MB response re-fetched after every mutation — the newest 100
     * threads (with all their replies) covers any realistic discussion.
     */
    private static final int MAX_COMMENT_THREADS_PER_NEWS = 100;
/**
 * Cap on replies loaded per thread (newest first, applied in SQL). A single
 * thread with thousands of replies (20KB content each) would otherwise make
 * the GET /comments response unbounded (~100MB at 5k replies). The cap is a
 * hard backstop — realistic threads stay well below it. (A "show more
 * replies" pagination path is a follow-up if it ever bites.)
 */
private static final int MAX_REPLIES_PER_THREAD = 50;
    /**
     * Cap on distinct users notified per comment. The mention id comes from
     * client-supplied HTML (data-user-id), so without a cap a single comment
     * could carry hundreds of ids and spam in-app + email notifications.
     */
    private static final int MAX_MENTIONS_PER_COMMENT = 10;
    /**
     * Anti-spam rate limit on comment creation (same pattern as
     * SkillCommentServiceImpl): a single comment can fan out to up to 10
     * in-app + email notifications, so an unthrottled endpoint is a
     * notification-spam vector. 10/min is far above any real typing pace.
     */
    private static final int COMMENT_MAX_PER_MINUTE = 10;
    private static final int COMMENT_WINDOW_SECONDS = 60;
    /**
     * Max nested reply depth (FR-17, v1.21): top-level = 1, replies up to
     * level 3. Replying to a level-3 comment is rejected with
     * COMMENT_DEPTH_EXCEEDED (same pattern as Skill Market's MAX_COMMENT_DEPTH).
     */
    private static final int MAX_COMMENT_DEPTH = 3;

    private final AiNewsRepository aiNewsRepository;
    private final AiNewsCommentRepository commentRepository;
    private final AiNewsCommentVoteRepository voteRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final EventNotificationService eventNotificationService;
    private final CommentHtmlSanitizer commentHtmlSanitizer;
    private final RateLimiter rateLimiter;

    /**
     * Runs the post-commit @mention notification work in its OWN transaction.
     * The afterCommit callback fires while the comment transaction is still
     * bound to the thread (Spring runs afterCommit BEFORE cleanup), so a
     * default PROPAGATION_REQUIRED template would PARTICIPATE in the already
     * committed transaction and never commit its writes. REQUIRES_NEW
     * suspends that bound transaction and starts a fresh one — same pattern
     * as AiNewsBatchService.sourceTransaction.
     */
    private final TransactionTemplate notificationTransaction;

    /** Public frontend base URL — appended to news links in notification emails. */
    @Value("${app.base-url:}")
    private String appBaseUrl;

    public AiNewsCommentServiceImpl(AiNewsRepository aiNewsRepository,
                                    AiNewsCommentRepository commentRepository,
                                    AiNewsCommentVoteRepository voteRepository,
                                    UserRepository userRepository,
                                    UserSettingsRepository userSettingsRepository,
                                    EventNotificationService eventNotificationService,
                                    CommentHtmlSanitizer commentHtmlSanitizer,
                                    RateLimiter rateLimiter,
                                    PlatformTransactionManager transactionManager) {
        this.aiNewsRepository = aiNewsRepository;
        this.commentRepository = commentRepository;
        this.voteRepository = voteRepository;
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.eventNotificationService = eventNotificationService;
        this.commentHtmlSanitizer = commentHtmlSanitizer;
        this.rateLimiter = rateLimiter;
        this.notificationTransaction = new TransactionTemplate(transactionManager);
        this.notificationTransaction.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiNewsCommentItem> comments(Long newsId, User viewer) {
        requireActiveNewsId(newsId);
        // Bounded fetch: the newest MAX_COMMENT_THREADS_PER_NEWS top-level
        // threads + at most MAX_REPLIES_PER_THREAD newest replies per
        // thread, both capped IN SQL. The old findByNewsId loaded every row
        // (content TEXT up to 20KB each) before capping in Java — a single
        // thread with thousands of replies made both the heap load and the
        // JSON response unbounded. The Java cap below is now a no-op
        // backstop (the SQL CTE already bounds the top-level set).
        List<AiNewsComment> all = commentRepository.findBoundedByNewsId(
                newsId, MAX_COMMENT_THREADS_PER_NEWS, MAX_REPLIES_PER_THREAD);
        if (all.isEmpty()) {
            return List.of();
        }
        // Backstop: keep the newest MAX_COMMENT_THREADS_PER_NEWS top-level
        // threads (with ALL their replies — capping individual comments would
        // orphan replies from dropped threads). Normally a no-op because the
        // SQL CTE already applied the same bound. Depth-3 replies are kept
        // when their depth-2 parent survived the filter.
        if (all.size() > MAX_COMMENT_THREADS_PER_NEWS) {
            java.util.Set<Long> keptThreads = all.stream()
                    .filter(c -> c.getParentId() == null)
                    .sorted(Comparator.comparing(AiNewsComment::getCreatedAt).reversed())
                    .limit(MAX_COMMENT_THREADS_PER_NEWS)
                    .map(AiNewsComment::getCommentId)
                    .collect(Collectors.toSet());
            java.util.Set<Long> keptReplies = all.stream()
                    .filter(c -> c.getParentId() != null && keptThreads.contains(c.getParentId()))
                    .map(AiNewsComment::getCommentId)
                    .collect(Collectors.toSet());
            all = all.stream()
                    .filter(c -> c.getParentId() == null
                            || keptThreads.contains(c.getParentId())
                            || keptReplies.contains(c.getParentId()))
                    .toList();
        }
        Set<Long> userIds = all.stream().map(AiNewsComment::getUserId).collect(Collectors.toSet());
        // Batched IN-clause fetches — findAllById issues one SELECT per id
        // (N+1): 50 comments from 20 users would mean ~70 round-trips.
        Map<Long, String> names = userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getName() == null ? "" : u.getName(),
                        (a, b) -> a));
        Set<Long> votedIds = voteRepository
                .findByUserIdAndCommentIdIn(viewer.getId(),
                        all.stream().map(AiNewsComment::getCommentId).collect(Collectors.toSet()))
                .stream().map(AiNewsCommentVote::getCommentId).collect(Collectors.toSet());

        // Visible = not deleted, OR deleted top-level that still has at least
        // one non-deleted reply (tombstone keeps the reply context, FR-19).
        Map<Long, Long> liveReplyCount = all.stream()
                .filter(c -> c.getDeletedAt() == null && c.getParentId() != null)
                .collect(Collectors.groupingBy(AiNewsComment::getParentId, Collectors.counting()));
        // A reply whose DIRECT parent is a DELETED REPLY (depth-2/3) is
        // hidden (FR-19 "reply con bị xóa → ẩn bình thường") — the frontend
        // nests it under that parent, so it would be invisible in the UI
        // anyway (FR-17 v1.21 depth-3 under a deleted depth-2). Replies
        // under a deleted TOP-LEVEL are kept — the tombstone needs them
        // (FR-19).
        java.util.Set<Long> deletedReplyIds = all.stream()
                .filter(c -> c.getDeletedAt() != null && c.getParentId() != null)
                .map(AiNewsComment::getCommentId)
                .collect(Collectors.toSet());

        return all.stream()
                .filter(c -> c.getDeletedAt() == null
                        || (c.getParentId() == null
                            && liveReplyCount.getOrDefault(c.getCommentId(), 0L) > 0))
                .filter(c -> c.getParentId() == null || !deletedReplyIds.contains(c.getParentId()))
                .sorted(Comparator
                        // top-level first, replies after
                        .comparing((AiNewsComment c) -> c.getParentId() == null ? 0 : 1)
                        // top-level: newest first (FR-16); replies: oldest first (FR-17)
                        .thenComparing((AiNewsComment a, AiNewsComment b) -> {
                            int cmp = a.getCreatedAt().compareTo(b.getCreatedAt());
                            // top-level: newest first; replies: oldest first
                            return a.getParentId() == null ? -cmp : cmp;
                        })
                        .thenComparing(AiNewsComment::getCommentId))
                .map(c -> toItem(c, names, viewer.getId(), votedIds))
                .toList();
    }

    @Override
    @Transactional
    public AiNewsCommentItem addComment(Long newsId, User viewer, AiNewsCommentRequest request) {
        // Anti-spam throttle (per user, all news): each comment can fan out
        // to up to 10 in-app + email notifications — an unthrottled endpoint
        // is a notification-spam vector (same pattern as skill comments).
        if (!rateLimiter.allow("ai-news-comment:" + viewer.getId(),
                COMMENT_MAX_PER_MINUTE, COMMENT_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.COMMENT_RATE_LIMITED,
                    "Too many requests. Please try again later.");
        }
        AiNews news = requireActiveNews(newsId);
        // Raw-input size guard BEFORE sanitizing: the sanitized-output cap
        // below cannot protect the Jsoup parse of an unbounded payload
        // (several× memory amplification → DoS). The frontend editor limits
        // to 1000 chars, but the API can be called directly.
        if (request.content() != null && request.content().length() > MAX_COMMENT_HTML_LENGTH) {
            throw new BusinessException(BusinessErrorCode.COMMENT_TOO_LONG,
                    "Comment content is too large.");
        }
        // FR-23: rich text — sanitize the editor HTML, then validate the
        // PLAIN TEXT (HTML tags and embedded images don't count toward the
        // 1000-character limit).
        String sanitized = commentHtmlSanitizer.sanitize(request.content());
        String plainText = Jsoup.parse(sanitized == null ? "" : sanitized).text().trim();
        if (plainText.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.COMMENT_EMPTY, "Comment content is empty.");
        }
        if (plainText.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException(BusinessErrorCode.COMMENT_TOO_LONG,
                    "Comment exceeds 1000 characters.");
        }
        // The plain-text limit does not bound the stored HTML (embedded
        // base64 images, nested markup) — reject oversized payloads.
        if (sanitized != null && sanitized.length() > MAX_COMMENT_HTML_LENGTH) {
            throw new BusinessException(BusinessErrorCode.COMMENT_TOO_LONG,
                    "Comment content is too large.");
        }

        Long effectiveParentId = request.parentId();
        if (effectiveParentId != null) {
            AiNewsComment parent = commentRepository.findById(effectiveParentId)
                    .orElseThrow(() -> new BusinessException(BusinessErrorCode.COMMENT_PARENT_NOT_FOUND,
                            "Parent comment not found."));
            if (!newsId.equals(parent.getNewsId())) {
                throw new BusinessException(BusinessErrorCode.COMMENT_PARENT_NOT_FOUND,
                        "Parent comment not found.");
            }
            if (parent.getDeletedAt() != null) {
                throw new BusinessException(BusinessErrorCode.COMMENT_PARENT_DELETED,
                        "Parent comment was deleted.");
            }
            // The thread root must still be alive too — otherwise the reply
            // would resurrect a tombstoned thread and inflate its counter.
            AiNewsComment root = threadRoot(parent);
            if (root.getDeletedAt() != null) {
                throw new BusinessException(BusinessErrorCode.COMMENT_PARENT_DELETED,
                        "Parent comment was deleted.");
            }
            // Depth > 3 → reject (FR-17, v1.21): nested replies are capped at
            // 3 levels (top-level = 1). The frontend hides the Reply button on
            // level-3 comments; this is the API-level guard.
            if (depthOf(parent) >= MAX_COMMENT_DEPTH) {
                throw new BusinessException(BusinessErrorCode.COMMENT_DEPTH_EXCEEDED,
                        "Replies are limited to 3 levels.");
            }
        }

        AiNewsComment comment = AiNewsComment.builder()
                .newsId(newsId)
                .userId(viewer.getId())
                .parentId(effectiveParentId)
                .content(sanitized)
                .build();
        commentRepository.save(comment);

        if (effectiveParentId != null) {
            commentRepository.incrementReplyCount(effectiveParentId);
        }

        // Best-effort @mention notifications — must never break the comment.
        // Runs AFTER the comment transaction commits (afterCommit) so the
        // SMTP round-trips (up to 10 emails) don't hold the comment's DB
        // connection open. The callback itself opens a NEW transaction
        // (notificationTransaction) — afterCommit fires with the comment
        // transaction already closed, so the notification writes need their
        // own. When no transaction is active (unit tests) it runs inline.
        try {
            runAfterCommit(() -> {
                try {
                    notificationTransaction.executeWithoutResult(
                            status -> notifyMentionedUsers(viewer, comment, news.getTitle()));
                } catch (Exception ex) {
                    log.error("AI News: @mention notification callback failed for comment {}: {}",
                            comment.getCommentId(), ex.getMessage(), ex);
                }
            });
        } catch (Exception e) {
            log.warn("AI News: could not send @mention notifications for comment {}: {}",
                    comment.getCommentId(), e.getMessage());
        }

        Map<Long, String> names = Map.of(viewer.getId(),
                viewer.getName() == null ? "" : viewer.getName());
        return toItem(comment, names, viewer.getId(), Set.of());
    }

    /**
     * Runs the action after the current transaction commits (so slow side
     * effects — SMTP sends — don't stretch the DB transaction). When no
     * transaction synchronization is active (e.g. unit tests) the action
     * runs inline, preserving the previous behavior.
     */
    private void runAfterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    @Override
    @Transactional
    public void deleteComment(Long newsId, Long commentId, User viewer) {
        requireActiveNewsId(newsId);
        AiNewsComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.COMMENT_NOT_FOUND,
                        "Comment not found."));
        if (!newsId.equals(comment.getNewsId())) {
            throw new BusinessException(BusinessErrorCode.COMMENT_NOT_FOUND, "Comment not found.");
        }
        boolean own = comment.getUserId().equals(viewer.getId());
        if (!own && !isAdmin(viewer)) {
            throw new BusinessException(BusinessErrorCode.COMMENT_NOT_OWN,
                    "You can only delete your own comments.");
        }
        // Atomic soft delete: the conditional UPDATE (WHERE deleted_at IS
        // NULL) is the idempotency guard — two concurrent deletes of the
        // same comment cannot both win, so the parent's reply counter is
        // decremented exactly once. A retry/duplicate DELETE (0 rows
        // affected) is a no-op.
        int deleted = commentRepository.softDeleteIfLive(commentId, Instant.now());
        if (deleted > 0 && comment.getParentId() != null) {
            // Keep the parent's reply counter in sync (soft delete — the row
            // stays, so the counter must be decremented here, not on purge).
            commentRepository.decrementReplyCount(comment.getParentId());
        }
    }

    @Override
    @Transactional
    public int toggleHelpful(Long newsId, Long commentId, User viewer) {
        requireActiveNewsId(newsId);
        AiNewsComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.COMMENT_NOT_FOUND,
                        "Comment not found."));
        if (!newsId.equals(comment.getNewsId()) || comment.getDeletedAt() != null) {
            throw new BusinessException(BusinessErrorCode.COMMENT_NOT_FOUND, "Comment not found.");
        }
        if (comment.getUserId().equals(viewer.getId())) {
            throw new BusinessException(BusinessErrorCode.CANNOT_VOTE_OWN_COMMENT,
                    "You cannot vote on your own comment.");
        }
        // Toggle: delete first; if nothing was removed, the user had not
        // voted → add the vote. ON CONFLICT DO NOTHING keeps the insert
        // race-safe (a concurrent vote is a no-op, not an exception).
        voteRepository.deleteVote(commentId, viewer.getId());
        if (voteRepository.upsertVote(commentId, viewer.getId()) > 0) {
            // Recompute (not +1/-1) the denormalized counter from the vote
            // table: idempotent and self-healing — concurrent first-time
            // votes converge on the same value instead of double-incrementing
            // (both pass the delete-first check, only one insert lands).
            commentRepository.recomputeHelpfulCount(commentId);
        }
        // Authoritative count from the DB — the in-memory entity's counter
        // can be stale under concurrent votes.
        return (int) voteRepository.countByCommentId(commentId);
    }

    // ── @mention notifications ───────────────────────────────────────────────

    /**
     * Sends in-app + email notifications to every user @mentioned in the
     * comment (best-effort — callers catch exceptions). The actor is never
     * notified about their own mention. Distinct recipients are capped at
     * {@link #MAX_MENTIONS_PER_COMMENT} (anti-spam — the ids are
     * client-supplied).
     */
    private void notifyMentionedUsers(User actor, AiNewsComment comment, String newsTitle) {
        Set<Long> mentionedIds = parseMentionUserIds(comment.getContent());
        if (mentionedIds.size() > MAX_MENTIONS_PER_COMMENT) {
            log.warn("AI News: comment {} mentions {} distinct users — capping at {}",
                    comment.getCommentId(), mentionedIds.size(), MAX_MENTIONS_PER_COMMENT);
        }
        // Batch-fetch users + settings upfront (up to 10 ids) — avoids up to
        // 20 sequential PK lookups inside the write transaction.
        Map<Long, User> userMap = userRepository.findByIdIn(mentionedIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Map<Long, UserSettings> settingsMap = userSettingsRepository.findAllById(mentionedIds).stream()
                .collect(Collectors.toMap(UserSettings::getUserId, s -> s, (a, b) -> a));

        int notified = 0;
        for (Long recipientId : mentionedIds) {
            if (notified >= MAX_MENTIONS_PER_COMMENT) {
                break;
            }
            User recipient = userMap.get(recipientId);
            if (recipient == null) {
                continue;
            }
            boolean sent = eventNotificationService.notify(actor, recipient,
                    settingsMap.get(recipientId),
                    new EventNotificationService.EventDetails(
                            actorName(actor) + " mentioned you in: " + newsTitle,
                            new EventNotificationService.EventContext(
                                    null, null, null,
                                    comment.getNewsId(), comment.getCommentId()),
                            "[AX Portal] AI News: " + actorName(actor) + " mentioned you",
                            mentionEmailBody(actor, newsTitle,
                                    comment.getNewsId(), comment.getCommentId())));
            // Count only ACTUAL notifications — skipped recipients (the
            // actor, disabled/missing users) must not consume the
            // anti-spam budget.
            if (sent) {
                notified++;
            }
        }
    }

    /** Extracts data-user-id values from mention spans (deduped, invalid ids dropped). */
    private Set<Long> parseMentionUserIds(String html) {
        Set<Long> ids = new LinkedHashSet<>();
        if (html == null || html.isBlank()) {
            return ids;
        }
        for (Element span :
                Jsoup.parseBodyFragment(html).select("span[data-type=mention][data-user-id]")) {
            try {
                ids.add(Long.parseLong(span.attr("data-user-id").trim()));
            } catch (NumberFormatException ignored) {
                // malformed id — skip
            }
        }
        return ids;
    }

    /** Display name of the actor for notification titles/subjects. */
    private static String actorName(User actor) {
        return actor.getName() != null ? actor.getName() : "Someone";
    }

    /** Email body for a @mention notification (title + deep link). */
    private String mentionEmailBody(User actor, String newsTitle, Long newsId, Long commentId) {
        String newsUrl = newsDetailUrl(newsId, commentId);
        return actorName(actor) + " mentioned you in \"" + newsTitle + "\"."
                + (newsUrl != null ? "\n\n" + newsUrl : "");
    }

    /** Frontend URL of the AI News page, scrolled to the mentioning comment. */
    private String newsDetailUrl(Long newsId, Long commentId) {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return null;
        }
        return appBaseUrl + "/ai-news?news=" + newsId + "&comment=" + commentId;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * Depth of a comment in its thread (top-level = 1). Walks the parent
     * chain; the loop cap guards against a corrupted cycle in the data.
     */
    private int depthOf(AiNewsComment comment) {
        int depth = 1;
        AiNewsComment current = comment;
        while (current.getParentId() != null && depth < 10) {
            AiNewsComment parent = commentRepository.findById(current.getParentId()).orElse(null);
            if (parent == null) {
                break;
            }
            current = parent;
            depth++;
        }
        return depth;
    }

    /**
     * The top-level comment of a thread (walks the parent chain up; the loop
     * cap guards against a corrupted cycle in the data).
     */
    private AiNewsComment threadRoot(AiNewsComment comment) {
        AiNewsComment current = comment;
        int hops = 0;
        while (current.getParentId() != null && hops < 10) {
            AiNewsComment parent = commentRepository.findById(current.getParentId()).orElse(null);
            if (parent == null) {
                break;
            }
            current = parent;
            hops++;
        }
        return current;
    }

    private AiNews requireActiveNews(Long newsId) {
        AiNews news = aiNewsRepository.findByNewsIdAndArchiveYn(newsId, "N");
        if (news == null) {
            // 404 NEWS_NOT_FOUND for both "not found" and "archived" — the reason is
            // not distinguished for the client (AI_News_Screen_SRS.md §5.4).
            throw new BusinessException(BusinessErrorCode.NEWS_NOT_FOUND,
                    "News not found or archived.");
        }
        return news;
    }

    /**
     * Active-existence guard WITHOUT loading the body TEXT column — for the
     * interaction endpoints (comments/like/view/vote/delete) that only need
     * to know the item exists and is active. Same 404 semantics as
     * {@link #requireActiveNews(Long)}.
     */
    private void requireActiveNewsId(Long newsId) {
        if (aiNewsRepository.countActiveById(newsId) == 0) {
            throw new BusinessException(BusinessErrorCode.NEWS_NOT_FOUND,
                    "News not found or archived.");
        }
    }

    private boolean isAdmin(User viewer) {
        return viewer.getRole() != null && "ADMIN_USER".equals(viewer.getRole().getName());
    }

    private AiNewsCommentItem toItem(AiNewsComment c, Map<Long, String> names,
                                     Long viewerId, Set<Long> votedIds) {
        return new AiNewsCommentItem(
                c.getCommentId(),
                c.getNewsId(),
                c.getParentId(),
                names.getOrDefault(c.getUserId(), "Deleted user"),
                c.getDeletedAt() != null ? null : c.getContent(),
                c.getHelpfulCount(),
                c.getReplyCount(),
                c.getCreatedAt(),
                c.getDeletedAt() != null,
                c.getUserId().equals(viewerId),
                votedIds.contains(c.getCommentId()));
    }
}
