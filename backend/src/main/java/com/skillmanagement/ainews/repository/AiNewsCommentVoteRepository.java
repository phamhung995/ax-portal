package com.skillmanagement.ainews.repository;

import com.skillmanagement.ainews.model.AiNewsCommentVote;
import com.skillmanagement.ainews.model.AiNewsCommentVote.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiNewsCommentVoteRepository extends JpaRepository<AiNewsCommentVote, Id> {

    boolean existsById(Id id);

    void deleteById(Id id);

    /**
     * Atomic upsert (PostgreSQL): inserts the vote if absent, no-ops if present.
     * Returns the row's xmax — 0 means the row was (re)inserted by this call,
     * >0 means it already existed. This makes the toggle race-free: two
     * concurrent inserts cannot both succeed (composite PK).
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO ai_news_comment_vote (comment_id, user_id, created_at) "
            + "VALUES (:commentId, :userId, now()) "
            + "ON CONFLICT (comment_id, user_id) DO NOTHING "
            + "RETURNING xmax", nativeQuery = true)
    Long upsertVote(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /** Native delete — bypasses the persistence context (no stale-entity flush). */
    @Modifying
    @Query(value = "DELETE FROM ai_news_comment_vote WHERE comment_id = :commentId AND user_id = :userId",
            nativeQuery = true)
    void deleteVote(@Param("commentId") Long commentId, @Param("userId") Long userId);

    long countByCommentId(Long commentId);
}
