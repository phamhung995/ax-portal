package com.furelise.skillmanagement.ainews.repository;

import com.furelise.skillmanagement.ainews.model.AiNews;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AiNewsRepository extends JpaRepository<AiNews, Long> {

    /**
     * List API rows (Home card / AI News screen list): no body column
     * (body can be very large — Home SRS §7.4). Newest first.
     */
    @Query(value = """
            WITH limited AS (
                SELECT news_id, title, sender, source, created_at, view_count
                FROM ai_news
                WHERE archive_yn = 'N'
                ORDER BY created_at DESC, news_id DESC
                LIMIT :limit
            )
            SELECT l.news_id, l.title, l.sender, l.source, l.created_at,
                   COALESCE(lk.like_count, 0) AS like_count,
                   COALESCE(cm.comment_count, 0) AS comment_count,
                   l.view_count
            FROM limited l
            LEFT JOIN (
                SELECT news_id, COUNT(*) AS like_count
                FROM ai_news_like WHERE news_id IN (SELECT news_id FROM limited)
                GROUP BY news_id
            ) lk ON lk.news_id = l.news_id
            LEFT JOIN (
                SELECT news_id, COUNT(*) AS comment_count
                FROM ai_news_comment WHERE deleted_at IS NULL
                  AND news_id IN (SELECT news_id FROM limited)
                GROUP BY news_id
            ) cm ON cm.news_id = l.news_id
            ORDER BY l.created_at DESC, l.news_id DESC
            """, nativeQuery = true)
    List<AiNewsListItem> findActiveList(@Param("limit") int limit);

    /** Detail of one active news item (includes body). */
    AiNews findByNewsIdAndArchiveYn(Long newsId, String archiveYn);

    /**
     * Lightweight active-existence check (no body column). The body is a large
     * TEXT blob; the 6 interaction endpoints (comments, like, view, vote,
     * delete) only need to know the item exists and is active, so they use
     * this instead of {@link #findByNewsIdAndArchiveYn} which would
     * deserialize the whole body into the JVM.
     */
    @Query(value = "SELECT COUNT(*) FROM ai_news WHERE news_id = :newsId AND archive_yn = 'N'",
            nativeQuery = true)
    long countActiveById(@Param("newsId") Long newsId);

    /** Title of one active news item (no body column). */
    @Query(value = "SELECT title FROM ai_news WHERE news_id = :newsId AND archive_yn = 'N'",
            nativeQuery = true)
    String findActiveTitleById(@Param("newsId") Long newsId);

    /**
     * Denormalized view count of one active news item (single PK lookup, no
     * body column). Used by recordView for repeat views: when the upsert
     * inserted nothing the counter is already current, so reading it is
     * cheaper than a COUNT(*) over the view log.
     */
    @Query(value = "SELECT view_count FROM ai_news WHERE news_id = :newsId AND archive_yn = 'N'",
            nativeQuery = true)
    Integer findActiveViewCountById(@Param("newsId") Long newsId);

    /**
     * Advanced-search list (AI News screen search box): same projection as
     * {@link #findActiveList} plus optional filters. Blank/null title, source,
     * sender and null from/to mean "no filter on that field" — with no
     * filters set the result equals {@code findActiveList(limit)}.
     *
     * <p>title/sender use case-insensitive substring match (ILIKE); source is
     * an exact match; from/to are calendar dates (to is inclusive of the
     * whole day — the service converts them to a [fromStart, toStart+1day)
     * instant range).</p>
     */
    @Query(value = """
            WITH limited AS (
                SELECT news_id, title, sender, source, created_at, view_count
                FROM ai_news
                WHERE archive_yn = 'N'
                  AND (CAST(:title AS varchar) IS NULL
                       OR title ILIKE '%' || :title || '%' ESCAPE '/')
                  AND (CAST(:source AS varchar) IS NULL OR source = :source)
                  AND (CAST(:sender AS varchar) IS NULL
                       OR sender ILIKE '%' || :sender || '%' ESCAPE '/')
                  AND (CAST(:from AS timestamp with time zone) IS NULL OR created_at >= :from)
                  AND (CAST(:to AS timestamp with time zone) IS NULL OR created_at < :to)
                ORDER BY created_at DESC, news_id DESC
                LIMIT :limit
            )
            SELECT l.news_id, l.title, l.sender, l.source, l.created_at,
                   COALESCE(lk.like_count, 0) AS like_count,
                   COALESCE(cm.comment_count, 0) AS comment_count,
                   l.view_count
            FROM limited l
            LEFT JOIN (
                SELECT news_id, COUNT(*) AS like_count
                FROM ai_news_like WHERE news_id IN (SELECT news_id FROM limited)
                GROUP BY news_id
            ) lk ON lk.news_id = l.news_id
            LEFT JOIN (
                SELECT news_id, COUNT(*) AS comment_count
                FROM ai_news_comment WHERE deleted_at IS NULL
                  AND news_id IN (SELECT news_id FROM limited)
                GROUP BY news_id
            ) cm ON cm.news_id = l.news_id
            ORDER BY l.created_at DESC, l.news_id DESC
            """, nativeQuery = true)
    List<AiNewsListItem> findActiveListFiltered(
            @Param("title") String title,
            @Param("source") String source,
            @Param("sender") String sender,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("limit") int limit);

    /** Dedup check (BR-NEWS-002). */
    boolean existsByMessageId(String messageId);

    /**
     * Archive news older than the cutoff (batch cleanup, BR-NEWS-010).
     * Also stamps {@code archived_at} (the flip time) so the incremental
     * daily image sweep can select only recently-archived ids.
     * Returns affected count.
     */
    @Modifying
    @Query(value = "UPDATE ai_news SET archive_yn = 'Y', archived_at = now() "
            + "WHERE archive_yn = 'N' AND created_at < :cutoff",
            nativeQuery = true)
    int archiveOlderThan(@Param("cutoff") Instant cutoff);

    /**
     * Ids archived since the given instant (incremental daily image sweep —
     * bounded by the 24h window instead of the ever-growing full archived
     * set). Rows with {@code archived_at IS NULL} (pre-V55, backfilled) are
     * outside the window — the weekly full sweep covers them.
     */
    @Query(value = "SELECT news_id FROM ai_news "
            + "WHERE archive_yn = 'Y' AND archived_at >= :since", nativeQuery = true)
    List<Long> findArchivedIdsSince(@Param("since") Instant since);

    /** All archived news ids (weekly full image sweep — self-healing for
     *  orphan folders from a crash between the archive flip and the file
     *  delete). */
    @Query(value = "SELECT news_id FROM ai_news WHERE archive_yn = 'Y'", nativeQuery = true)
    List<Long> findArchivedIds();

    /** Projection for the list API — no body. Includes like + comment counts
     *  for list sorting (most liked / most commented) + the denormalized
     *  distinct-viewer counter. */
    record AiNewsListItem(
            Long newsId,
            String title,
            String sender,
            String source,
            Instant createdAt,
            Long likeCount,
            Long commentCount,
            Integer viewCount
    ) {
    }
}
