package com.skillmanagement.skillrequest.repository;

import com.skillmanagement.skillrequest.model.SkillRequestCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface SkillRequestCommentLikeRepository
        extends JpaRepository<SkillRequestCommentLike, SkillRequestCommentLike.SkillRequestCommentLikeId> {

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    void deleteByCommentIdAndUserId(Long commentId, Long userId);

    /** Comment ids liked by the user (for the likedByMe flag, one query per page). */
    Set<Long> findCommentIdByUserId(Long userId);

    List<SkillRequestCommentLike> findByCommentIdIn(List<Long> commentIds);
}
