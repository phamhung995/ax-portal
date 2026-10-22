package com.skillmanagement.skill.repository;

import com.skillmanagement.skill.model.SkillComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SkillCommentRepository extends JpaRepository<SkillComment, Long> {

    Page<SkillComment> findBySkillIdAndDeletedAtIsNull(Long skillId, Pageable pageable);

    List<SkillComment> findBySkillIdAndDeletedAtIsNull(Long skillId);

    @Query("SELECT COUNT(c) FROM SkillComment c WHERE c.skillId = :skillId AND c.deletedAt IS NULL")
    long countBySkillIdAndDeletedAtIsNull(@org.springframework.data.repository.query.Param("skillId") Long skillId);
}
