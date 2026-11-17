package com.furelise.skillmanagement.ainews.service.impl;

import com.furelise.skillmanagement.ainews.dto.AiNewsDetail;
import com.furelise.skillmanagement.ainews.dto.AiNewsItem;
import com.furelise.skillmanagement.ainews.dto.AiNewsSearchCriteria;
import com.furelise.skillmanagement.ainews.dto.MentionUserItem;
import com.furelise.skillmanagement.ainews.model.AiNews;
import com.furelise.skillmanagement.ainews.model.AiNewsLike;
import com.furelise.skillmanagement.ainews.repository.AiNewsLikeRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsViewLogRepository;
import com.furelise.skillmanagement.ainews.service.AiNewsService;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.reputation.model.ReputationScore;
import com.furelise.skillmanagement.reputation.service.ReputationService;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * AI News screen + Home card data (AI_News_Screen_SRS.md §5) — news content
 * (list/search/detail), like, view tracking, and @mention autocomplete.
 * Comment operations (FR-16..FR-20) live in {@link AiNewsCommentServiceImpl}.
 */
@Service
@Slf4j
public class AiNewsServiceImpl implements AiNewsService {

    private static final int MENTION_SEARCH_LIMIT = 10;

    /**
     * Timezone used to interpret the advanced-search "from"/"to" calendar
     * dates. All users are in Vietnam and the frontend renders created_at in
     * the browser's (VN) timezone, so day boundaries must be VN-local. Hardcoded
     * (not systemDefault) because the Docker prod container runs the JVM in UTC.
     */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AiNewsRepository aiNewsRepository;
    private final AiNewsLikeRepository likeRepository;
    private final AiNewsViewLogRepository viewLogRepository;
    private final UserRepository userRepository;
    private final ReputationService reputationService;

    /**
     * Runs the view-log upsert in its OWN transaction (PROPAGATION_REQUIRES_NEW).
     * A concurrent duplicate insert that loses the unique (news_id, user_id)
     * race aborts the transaction on PostgreSQL — and an aborted transaction
     * poisons every subsequent statement on that connection. Containing the
     * upsert in a separate, self-rolling-back transaction keeps this method's
     * transaction clean so the recompute + count below still run.
     */
    private final TransactionTemplate viewTransaction;

    public AiNewsServiceImpl(AiNewsRepository aiNewsRepository,
                             AiNewsLikeRepository likeRepository,
                             AiNewsViewLogRepository viewLogRepository,
                             UserRepository userRepository,
                             PlatformTransactionManager transactionManager,
                             ReputationService reputationService) {
        this.aiNewsRepository = aiNewsRepository;
        this.likeRepository = likeRepository;
        this.viewLogRepository = viewLogRepository;
        this.userRepository = userRepository;
        this.reputationService = reputationService;
        this.viewTransaction = new TransactionTemplate(transactionManager);
        this.viewTransaction.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiNewsItem> list(int limit) {
        List<AiNewsRepository.AiNewsListItem> rows = aiNewsRepository.findActiveList(limit);
        Map<Long, ReputationScore> reputations = loadReputations(rows);
        return rows.stream()
                .map(row -> toItem(row, reputations))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiNewsItem> search(AiNewsSearchCriteria criteria, int limit) {
        if (criteria.from() != null && criteria.to() != null
                && criteria.from().isAfter(criteria.to())) {
            throw new BusinessException(BusinessErrorCode.NEWS_INVALID_DATE_RANGE,
                    "'from' date must not be after 'to' date.");
        }
        // Calendar dates → instant range: [from 00:00, to 23:59:59.999]
        // expressed as [fromStart, toStart + 1 day). The frontend renders
        // created_at in the user's browser timezone (all users are in Vietnam),
        // so the day boundaries must be computed in Asia/Ho_Chi_Minh to match
        // what the user sees — NOT ZoneId.systemDefault(), which is UTC inside
        // the Docker prod container (no TZ set) and would be off by 7h.
        Instant from = criteria.from() == null ? null
                : criteria.from().atStartOfDay(DISPLAY_ZONE).toInstant();
        Instant to = criteria.to() == null ? null
                : criteria.to().plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant();
        List<AiNewsRepository.AiNewsListItem> rows = aiNewsRepository.findActiveListFiltered(
                        escapeLike(blankToNull(criteria.title())),
                        blankToNull(criteria.source()),
                        escapeLike(blankToNull(criteria.sender())),
                        from, to, limit);
        Map<Long, ReputationScore> reputations = loadReputations(rows);
        List<AiNewsItem> items = rows.stream()
                .map(row -> toItem(row, reputations))
                .toList();
        if ("reputation".equalsIgnoreCase(criteria.sortBy())) {
            // Reputation sort is best-first by cached score (null = not
            // enough views yet, sorts last), tie-break newest first.
            // NOTE: the native query already LIMITs to the newest `limit`
            // rows, so this sort applies within that window — the news
            // screen has no pagination, and the window is exactly what it
            // displays (max 50).
            return items.stream()
                    .sorted(Comparator.comparingDouble(
                                    (AiNewsItem item) -> item.reputationScore() != null
                                            ? item.reputationScore() : -1.0).reversed()
                            .thenComparing(AiNewsItem::createdAt, Comparator.reverseOrder())
                            .thenComparing(AiNewsItem::newsId, Comparator.reverseOrder()))
                    .toList();
        }
        return items;
    }

    private AiNewsItem toItem(AiNewsRepository.AiNewsListItem row,
                              Map<Long, ReputationScore> reputations) {
        ReputationScore rep = reputations.get(row.newsId());
        return new AiNewsItem(
                row.newsId(), row.title(), row.sender(), row.source(), row.createdAt(),
                row.likeCount(), row.commentCount(), row.viewCount(),
                rep != null && rep.getScore() != null ? rep.getScore().doubleValue() : null,
                rep != null ? rep.getTier() : "NEW");
    }

    private Map<Long, ReputationScore> loadReputations(List<AiNewsRepository.AiNewsListItem> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        return reputationService.getReputations("AI_NEWS",
                rows.stream().map(AiNewsRepository.AiNewsListItem::newsId).toList());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Escapes LIKE wildcards so user input is matched literally — a search
     * for "100%" must not behave like a SQL pattern. The query uses
     * {@code ILIKE ... ESCAPE '/'} (see AiNewsRepository).
     */
    private static String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("/", "//").replace("%", "/%").replace("_", "/_");
    }

    @Override
    @Transactional(readOnly = true)
    public AiNewsDetail detail(Long newsId, User viewer) {
        AiNews news = requireActiveNews(newsId);
        AiNewsLike.Id likeId = new AiNewsLike.Id(newsId, viewer.getId());
        boolean liked = likeRepository.existsById(likeId);
        long likeCount = likeRepository.countByNewsIdNative(newsId);
        return new AiNewsDetail(
                news.getNewsId(), news.getTitle(), news.getSender(), news.getSource(),
                news.getBody(), news.getCreatedAt(), (int) likeCount, liked,
                news.getViewCount());
    }

    @Override
    public int recordView(Long newsId, User viewer) {
        requireActiveNewsId(newsId);
        // No exists pre-check: the upsert's WHERE NOT EXISTS guard is the
        // idempotency guard. A pre-check would be TOCTOU — two concurrent
        // first-time views (two tabs) would both pass it, both increment,
        // and drift the denormalized counter.
        // The upsert + recompute run in ONE short transaction
        // (viewTransaction, REQUIRES_NEW): a concurrent duplicate insert
        // that loses the unique (news_id, user_id) race aborts that
        // transaction on PostgreSQL — an aborted transaction rejects every
        // further statement on that connection, so the recompute must live
        // inside it (the template rolls back; the loser's insert is a
        // no-op and the winner's recompute converges the count).
        // No @Transactional here: a surrounding transaction would hold a
        // second pooled connection across the REQUIRES_NEW suspension
        // (~5 concurrent viewers exhaust the default pool of 10).
        // requireActiveNewsId / the count reads are single statements that
        // each check out and release their own connection.
        boolean firstTime;
        try {
            firstTime = viewTransaction.execute(status -> {
                int inserted = viewLogRepository.upsertView(newsId, viewer.getId());
                if (inserted > 0) {
                    // First-time view: recompute (not +1) the denormalized
                    // counter from the log — idempotent and self-healing,
                    // concurrent first-time views converge on the same
                    // value instead of double-incrementing.
                    viewLogRepository.recomputeViewCount(newsId);
                }
                // Repeat view (inserted == 0): the counter is already
                // current — skip the row-locking UPDATE + COUNT(*).
                return inserted > 0;
            });
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            // Concurrent duplicate insert lost the unique (news_id, user_id)
            // race — treat as a no-op (the other insert won, and its
            // recompute is committed by the time this insert fails).
            firstTime = false;
        }
        if (firstTime) {
            // Reputation (BA §9.5): a first-time view moves the news score.
            // (Repeat views do not change any signal — no refresh needed.)
            reputationService.refreshAiNews(newsId);
            // Authoritative count from the log (the recompute wrote the
            // same value, but the log is the source of truth).
            return (int) viewLogRepository.countByNewsId(newsId);
        }
        // Repeat view / race loser: read the already-current denormalized
        // counter (single PK lookup) instead of a COUNT(*) over the log.
        Integer cached = aiNewsRepository.findActiveViewCountById(newsId);
        return cached != null ? cached : 0;
    }

    @Override
    @Transactional
    public int toggleLike(Long newsId, User viewer) {
        requireActiveNewsId(newsId);
        // Toggle: delete first; if nothing was removed, the user had not
        // liked → add the like. ON CONFLICT DO NOTHING keeps the insert
        // race-safe (a concurrent like is a no-op, not an exception).
        if (likeRepository.deleteLike(newsId, viewer.getId()) == 0) {
            likeRepository.upsertLike(newsId, viewer.getId());
        }
        // Reputation (BA §9.5): a like/unlike moves the news score.
        reputationService.refreshAiNews(newsId);
        return (int) likeRepository.countByNewsIdNative(newsId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MentionUserItem> mentionUsers(String query) {
        String q = query == null ? null : query.trim();
        Specification<User> spec = (root, cq, cb) -> {
            var criteria = cb.equal(root.get("enabled"), true);
            if (q != null && !q.isEmpty()) {
                // Escape LIKE wildcards so a query of "%" or "a_b" matches
                // literally (same policy as the news advanced search).
                String like = "%" + escapeLike(q.toLowerCase()) + "%";
                criteria = cb.and(criteria, cb.or(
                        cb.like(cb.lower(root.get("name")), like, '/'),
                        cb.like(cb.lower(root.get("email")), like, '/')));
            }
            return criteria;
        };
        // Explicit sort: stable dropdown results as the query grows
        // (name asc, id as tie-breaker for duplicate names).
        return userRepository.findAll(spec,
                        PageRequest.of(0, MENTION_SEARCH_LIMIT,
                                Sort.by(Sort.Direction.ASC, "name").and(Sort.by("id"))))
                .stream()
                .map(u -> new MentionUserItem(u.getId(), u.getName()))
                .toList();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

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
}
