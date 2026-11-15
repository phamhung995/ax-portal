package com.furelise.skillmanagement.ainews.repository;

import com.furelise.skillmanagement.ainews.model.AiNewsComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiNewsCommentRepository extends JpaRepository<AiNewsComment, Long> {

    /**
     * Bounded comment fetch: the {@code limit} NEWEST top-level threads
     * (created_at DESC, comment_id DESC) plus at most {@code replyLimit}
     * NEWEST replies per parent at BOTH nested levels (depth-2 under the
     * top-level, depth-3 under a depth-2 reply). All bounds are applied in
     * SQL so a single thread with thousands of replies cannot make the heap
     * load or the JSON response unbounded (content TEXT up to 20KB per row).
     * Deleted rows are included — tombstone logic lives in the service.
     */
    @Query(value = """
            WITH kept AS (
                SELECT comment_id
                FROM ai_news_comment
                WHERE news_id = :newsId AND parent_id IS NULL
                ORDER BY created_at DESC, comment_id DESC
                LIMIT :limit
            ),
            ranked_replies2 AS (
                SELECT c.comment_id,
                       ROW_NUMBER() OVER (
                           PARTITION BY c.parent_id
                           ORDER BY c.created_at DESC, c.comment_id DESC
                       ) AS rn
                FROM ai_news_comment c
                WHERE c.news_id = :newsId
                  AND c.parent_id IN (SELECT comment_id FROM kept)
            ),
            ranked_replies3 AS (
                SELECT c.comment_id,
                       ROW_NUMBER() OVER (
                           PARTITION BY p2.parent_id
                           ORDER BY c.created_at DESC, c.comment_id DESC
                       ) AS rn
                FROM ai_news_comment c
                JOIN ai_news_comment p2 ON p2.comment_id = c.parent_id
                WHERE c.news_id = :newsId
                  AND c.parent_id IN (SELECT comment_id FROM ranked_replies2
                                      WHERE rn <= :replyLimit)
            )
            SELECT c.comment_id, c.news_id, c.user_id, c.parent_id, c.content,
                   c.helpful_count, c.reply_count, c.created_at, c.deleted_at
            FROM ai_news_comment c
            WHERE c.news_id = :newsId
              AND (c.parent_id IS NULL
                   OR c.comment_id IN (SELECT comment_id FROM ranked_replies2
                                       WHERE rn <= :replyLimit)
                   OR c.comment_id IN (SELECT comment_id FROM ranked_replies3
                                       WHERE rn <= :replyLimit))
            """, nativeQuery = true)
    List<AiNewsComment> findBoundedByNewsId(@Param("newsId") Long newsId,
                                            @Param("limit") int limit,
                                            @Param("replyLimit") int replyLimit);

    /**
     * Recompute the helpful counter from the vote table. Idempotent +
     * self-healing: concurrent first-time votes converge on the same value
     * (unlike {@code helpful_count + 1}, which drifts when two votes race —
     * both pass the delete-first check, only one insert lands, both increment).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE ai_news_comment c SET helpful_count = ("
            + "SELECT COUNT(*) FROM ai_news_comment_vote v WHERE v.comment_id = :commentId"
            + ") WHERE comment_id = :commentId", nativeQuery = true)
    void recomputeHelpfulCount(@Param("commentId") Long commentId);

    /** Atomically increment the reply counter of a top-level comment. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AiNewsComment c SET c.replyCount = c.replyCount + 1 WHERE c.commentId = :commentId")
    void incrementReplyCount(@org.springframework.data.repository.query.Param("commentId") Long commentId);

    /**
     * Decrement the reply counter when a reply is deleted (guarded at 0 —
     * the counter is maintained by these increments/decrements, so a
     * negative value would only come from a bug, not from real data).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AiNewsComment c SET c.replyCount = GREATEST(c.replyCount - 1, 0) WHERE c.commentId = :commentId")
    void decrementReplyCount(@org.springframework.data.repository.query.Param("commentId") Long commentId);

    /**
     * Conditional soft delete: sets deleted_at ONLY when the row is still
     * live. Atomic — two concurrent deletes of the same comment cannot both
     * "win" (the loser's UPDATE affects 0 rows), which would otherwise
     * double-decrement the parent's reply counter.
     *
     * @return 1 when this call performed the delete, 0 when the comment was
     *         already deleted (or does not exist).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AiNewsComment c SET c.deletedAt = :deletedAt "
            + "WHERE c.commentId = :commentId AND c.deletedAt IS NULL")
    int softDeleteIfLive(@org.springframework.data.repository.query.Param("commentId") Long commentId,
                         @org.springframework.data.repository.query.Param("deletedAt") java.time.Instant deletedAt);
}
