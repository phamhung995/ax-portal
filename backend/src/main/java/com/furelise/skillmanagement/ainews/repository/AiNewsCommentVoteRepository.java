package com.furelise.skillmanagement.ainews.repository;

import com.furelise.skillmanagement.ainews.model.AiNewsCommentVote;
import com.furelise.skillmanagement.ainews.model.AiNewsCommentVote.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AiNewsCommentVoteRepository extends JpaRepository<AiNewsCommentVote, Id> {

    /**
     * Remove the vote if present (PostgreSQL). Returns 1 when the vote
     * existed and was removed, 0 when it did not exist — used by the toggle
     * to decide the direction.
     */
    @Modifying
    @Query(value = "DELETE FROM ai_news_comment_vote WHERE comment_id = :commentId AND user_id = :userId",
            nativeQuery = true)
    int deleteVote(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /**
     * Insert a vote if absent, no-op if present (PostgreSQL). Returns the
     * affected-row count: 1 when the vote was inserted, 0 when it already
     * existed — the composite PK (comment_id, user_id) makes concurrent
     * inserts safe (a losing race is a no-op, not an exception).
     */
    @Modifying
    @Query(value = "INSERT INTO ai_news_comment_vote (comment_id, user_id, created_at) "
            + "VALUES (:commentId, :userId, now()) "
            + "ON CONFLICT (comment_id, user_id) DO NOTHING", nativeQuery = true)
    int upsertVote(@Param("commentId") Long commentId, @Param("userId") Long userId);

    long countByCommentId(Long commentId);

    /**
     * Single IN-clause fetch of the viewer's votes for a set of comments.
     * Replaces {@code findAllById} (one SELECT per comment — N+1) in the
     * AI News comment list.
     */
    @Query("select v from AiNewsCommentVote v where v.userId = :userId and v.commentId in :commentIds")
    List<AiNewsCommentVote> findByUserIdAndCommentIdIn(
            @Param("userId") Long userId, @Param("commentIds") Collection<Long> commentIds);
}
