package com.furelise.skillmanagement.skill.repository;

import com.furelise.skillmanagement.skill.model.SkillCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SkillCommentLikeRepository extends JpaRepository<SkillCommentLike, SkillCommentLike.Id> {

    List<SkillCommentLike> findByUserIdInAndCommentIdIn(Collection<Long> userIds, Collection<Long> commentIds);

    void deleteByCommentId(Long commentId);
}
