package com.skillmanagement.ainews.repository;

import com.skillmanagement.ainews.model.AiNewsComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AiNewsCommentRepository extends JpaRepository<AiNewsComment, Long> {

    /** All comments of a news item (deleted included — tombstone logic in service). */
    List<AiNewsComment> findByNewsId(Long newsId);

    /** All (non-deleted) comments of a news item, newest first. */
    List<AiNewsComment> findByNewsIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long newsId);

    /** Reply count of a top-level comment (non-deleted children). */
    long countByParentIdAndDeletedAtIsNull(Long parentId);

    /** Increment/decrement the helpful counter. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AiNewsComment c SET c.helpfulCount = c.helpfulCount + :delta WHERE c.commentId = :commentId")
    void incrementHelpfulCount(@org.springframework.data.repository.query.Param("commentId") Long commentId,
                               @org.springframework.data.repository.query.Param("delta") int delta);

    /** Atomically increment the reply counter of a top-level comment. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AiNewsComment c SET c.replyCount = c.replyCount + 1 WHERE c.commentId = :commentId")
    void incrementReplyCount(@org.springframework.data.repository.query.Param("commentId") Long commentId);
}
