package com.skillmanagement.skill.repository;

import com.skillmanagement.skill.model.SkillStageMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Repository for skill_stage_mapping.
 */
public interface SkillStageMappingRepository extends JpaRepository<SkillStageMapping, SkillStageMapping.SkillStageMappingKey> {

    List<SkillStageMapping> findBySkillIdIn(Collection<Long> skillIds);

    List<SkillStageMapping> findBySkillId(Long skillId);

    List<SkillStageMapping> findByStageId(Long stageId);

    void deleteBySkillId(Long skillId);

    void deleteByStageIdIn(Collection<Long> stageIds);

    long countByStageId(Long stageId);

    /** Groups the mappings of the given skills by skill id. */
    static Map<Long, List<Long>> groupBySkill(List<SkillStageMapping> mappings) {
        return mappings.stream().collect(Collectors.groupingBy(
                SkillStageMapping::getSkillId,
                Collectors.mapping(SkillStageMapping::getStageId, Collectors.toList())));
    }
}
