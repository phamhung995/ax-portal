package com.furelise.skillmanagement.ainews.repository;

import com.furelise.skillmanagement.ainews.model.AiNewsLike;
import com.furelise.skillmanagement.ainews.model.AiNewsLike.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiNewsLikeRepository extends JpaRepository<AiNewsLike, Id> {

    boolean existsById(Id id);

    /**
     * Remove the like if present (PostgreSQL). Returns 1 when the like
     * existed and was removed, 0 when it did not exist — used by the toggle
     * to decide the direction.
     */
    @Modifying
    @Query(value = "DELETE FROM ai_news_like WHERE news_id = :newsId AND user_id = :userId",
            nativeQuery = true)
    int deleteLike(@Param("newsId") Long newsId, @Param("userId") Long userId);

    /**
     * Insert a like if absent, no-op if present (PostgreSQL). Void return —
     * the composite PK (news_id, user_id) makes concurrent inserts safe: a
     * losing race is simply a no-op.
     */
    @Modifying
    @Query(value = "INSERT INTO ai_news_like (news_id, user_id, created_at) "
            + "VALUES (:newsId, :userId, now()) "
            + "ON CONFLICT (news_id, user_id) DO NOTHING", nativeQuery = true)
    void upsertLike(@Param("newsId") Long newsId, @Param("userId") Long userId);

    /** Count of likes for one news item (native — bypasses L1 cache). */
    @Query(value = "SELECT COUNT(*) FROM ai_news_like WHERE news_id = :newsId", nativeQuery = true)
    long countByNewsIdNative(@Param("newsId") Long newsId);
}
