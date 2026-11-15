package com.furelise.skillmanagement.skillrequest.repository;

import com.furelise.skillmanagement.skillrequest.model.SkillRequestComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SkillRequestCommentRepository extends JpaRepository<SkillRequestComment, Long> {

    Page<SkillRequestComment> findByRequestIdAndDeletedAtIsNull(Long requestId, Pageable pageable);

    /** Top-level comments only (thread roots) — the paged list. */
    Page<SkillRequestComment> findByRequestIdAndParentIdIsNullAndDeletedAtIsNull(Long requestId, Pageable pageable);

    /** All non-deleted comments of a request (reply tree assembly). */
    List<SkillRequestComment> findByRequestIdAndDeletedAtIsNull(Long requestId);

    @Query("SELECT COUNT(c) FROM SkillRequestComment c WHERE c.requestId = :requestId AND c.deletedAt IS NULL")
    long countByRequestIdAndDeletedAtIsNull(@Param("requestId") Long requestId);

    /** Non-deleted comments of the given requests (feed preview, one query per page). */
    List<SkillRequestComment> findByRequestIdInAndDeletedAtIsNull(List<Long> requestIds);

    /** Skill links per user (non-deleted comments linking a skill) — sidebar "Top Contributors". */
    @Query("""
            SELECT c.userId AS userId, COUNT(c.id) AS linkCount
            FROM SkillRequestComment c
            WHERE c.deletedAt IS NULL AND c.linkedSkillId IS NOT NULL
            GROUP BY c.userId
            """)
    List<Object[]> countSkillLinksByUser();

    /** Bulk delete all comments of a request (DB cascade handles comment_likes). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM SkillRequestComment c WHERE c.requestId = :requestId")
    void deleteByRequestId(@org.springframework.data.repository.query.Param("requestId") Long requestId);
}
