package com.furelise.skillmanagement.ainews.repository;

import com.furelise.skillmanagement.ainews.model.AiNewsViewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiNewsViewLogRepository extends JpaRepository<AiNewsViewLog, Long> {

    /**
     * Record a view if absent, no-op if present. Portable SQL (PostgreSQL +
     * H2 in PostgreSQL mode — H2 does not support ON CONFLICT): a plain
     * INSERT guarded by WHERE NOT EXISTS. Under the READ COMMITTED isolation
     * used by both databases, a concurrent duplicate insert is rejected by
     * the unique (news_id, user_id) constraint — the caller treats that
     * DataIntegrityViolationException as a no-op (the recompute below is
     * idempotent, so the count is still correct).
     *
     * @return 1 when a new view row was inserted, 0 when the view already
     *         existed (repeat view — the caller can skip the counter
     *         recompute and read the already-current denormalized value).
     */
    @Modifying
    @Query(value = "INSERT INTO ai_news_view_log (news_id, user_id, viewed_at) "
            + "SELECT :newsId, :userId, now() "
            + "WHERE NOT EXISTS ("
            + "SELECT 1 FROM ai_news_view_log "
            + "WHERE news_id = :newsId AND user_id = :userId)", nativeQuery = true)
    int upsertView(@Param("newsId") Long newsId, @Param("userId") Long userId);

    /** Distinct-viewer count for one news item (native — bypasses L1 cache). */
    @Query(value = "SELECT COUNT(*) FROM ai_news_view_log WHERE news_id = :newsId", nativeQuery = true)
    long countByNewsId(@Param("newsId") Long newsId);

    /**
     * Recompute the denormalized ai_news.view_count from the view log.
     * Idempotent + self-healing: concurrent calls converge on the same value
     * (unlike view_count + 1, which drifts when two first-time views race —
     * both would increment but only one row is inserted).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE ai_news SET view_count = ("
            + "SELECT COUNT(*) FROM ai_news_view_log v WHERE v.news_id = :newsId"
            + ") WHERE news_id = :newsId", nativeQuery = true)
    void recomputeViewCount(@Param("newsId") Long newsId);
}
