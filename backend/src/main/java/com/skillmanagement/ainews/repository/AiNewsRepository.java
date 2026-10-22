package com.skillmanagement.ainews.repository;

import com.skillmanagement.ainews.model.AiNews;
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
            SELECT news_id, title, sender, source, created_at
            FROM ai_news
            WHERE archive_yn = 'N'
            ORDER BY created_at DESC, news_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<AiNewsListItem> findActiveList(@Param("limit") int limit);

    /** Detail of one active news item (includes body). */
    AiNews findByNewsIdAndArchiveYn(Long newsId, String archiveYn);

    /** Dedup check (BR-NEWS-002). */
    boolean existsByMessageId(String messageId);

    /** Archive news older than the cutoff (batch cleanup, BR-NEWS-010). Returns affected count. */
    @Modifying
    @Query(value = "UPDATE ai_news SET archive_yn = 'Y' WHERE archive_yn = 'N' AND created_at < :cutoff",
            nativeQuery = true)
    int archiveOlderThan(@Param("cutoff") Instant cutoff);

    /** Projection for the list API — no body. */
    record AiNewsListItem(
            Long newsId,
            String title,
            String sender,
            String source,
            Instant createdAt
    ) {
    }
}
