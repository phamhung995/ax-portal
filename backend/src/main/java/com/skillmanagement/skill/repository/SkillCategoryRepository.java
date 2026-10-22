package com.skillmanagement.skill.repository;

import com.skillmanagement.skill.model.SkillCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for skill_category.
 */
public interface SkillCategoryRepository extends JpaRepository<SkillCategory, Long> {

    List<SkillCategory> findAllByOrderByCreatedAtAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
