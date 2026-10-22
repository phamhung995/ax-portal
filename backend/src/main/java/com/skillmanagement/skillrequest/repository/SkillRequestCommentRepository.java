package com.skillmanagement.skillrequest.repository;

import com.skillmanagement.skillrequest.model.SkillRequestComment;
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

    /** Resolved (non-deleted) comments grouped by request creator — sidebar "Top Requesters". */
    @Query("""
            SELECT r.createdBy AS userId, COUNT(c.id) AS resolvedCount
            FROM SkillRequestComment c
            JOIN SkillRequest r ON r.id = c.requestId
            WHERE c.deletedAt IS NULL AND c.resolvedYn = 'Y'
            GROUP BY r.createdBy
            """)
    List<Object[]> countResolvedByRequestCreator();
}
