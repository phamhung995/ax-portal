package com.skillmanagement.ainews.repository;

import com.skillmanagement.ainews.model.AiNewsLike;
import com.skillmanagement.ainews.model.AiNewsLike.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiNewsLikeRepository extends JpaRepository<AiNewsLike, Id> {

    boolean existsById(Id id);

    void deleteById(Id id);

    /**
     * Atomic upsert (PostgreSQL): inserts the like if absent, no-ops if present.
     * Returns the row's xmax — 0 means the row was (re)inserted by this call,
     * >0 means it already existed. This makes the toggle race-free: two
     * concurrent inserts cannot both succeed (composite PK).
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO ai_news_like (news_id, user_id, created_at) "
            + "VALUES (:newsId, :userId, now()) "
            + "ON CONFLICT (news_id, user_id) DO NOTHING "
            + "RETURNING xmax", nativeQuery = true)
    Long upsertLike(@Param("newsId") Long newsId, @Param("userId") Long userId);

    /** Native delete — bypasses the persistence context (no stale-entity flush). */
    @Modifying
    @Query(value = "DELETE FROM ai_news_like WHERE news_id = :newsId AND user_id = :userId",
            nativeQuery = true)
    void deleteLike(@Param("newsId") Long newsId, @Param("userId") Long userId);

    /** Count of likes for one news item (native — bypasses L1 cache). */
    @Query(value = "SELECT COUNT(*) FROM ai_news_like WHERE news_id = :newsId", nativeQuery = true)
    long countByNewsIdNative(@Param("newsId") Long newsId);

    /** Like counts for a set of news ids (one row per news). */
    List<AiNewsLike> findByNewsIdIn(List<Long> newsIds);
}
