package com.skillmanagement.skill.repository;

import com.skillmanagement.skill.model.SkillCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Repository for skill_category_mapping.
 */
public interface SkillCategoryMappingRepository extends JpaRepository<SkillCategoryMapping, SkillCategoryMapping.SkillCategoryMappingKey> {

    List<SkillCategoryMapping> findBySkillIdIn(Collection<Long> skillIds);

    List<SkillCategoryMapping> findBySkillId(Long skillId);

    List<SkillCategoryMapping> findByCategoryId(Long categoryId);

    void deleteBySkillId(Long skillId);

    void deleteByCategoryIdIn(Collection<Long> categoryIds);

    long countByCategoryId(Long categoryId);

    /** Groups the mappings of the given skills by skill id. */
    static Map<Long, List<Long>> groupBySkill(List<SkillCategoryMapping> mappings) {
        return mappings.stream().collect(Collectors.groupingBy(
                SkillCategoryMapping::getSkillId,
                Collectors.mapping(SkillCategoryMapping::getCategoryId, Collectors.toList())));
    }
}
