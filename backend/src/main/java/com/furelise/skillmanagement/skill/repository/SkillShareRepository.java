package com.furelise.skillmanagement.skill.repository;

import com.furelise.skillmanagement.skill.model.SkillShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SkillShareRepository extends JpaRepository<SkillShare, Long> {

    List<SkillShare> findBySkillIdOrderByCreatedAtAsc(Long skillId);

    List<SkillShare> findBySkillIdIn(Collection<Long> skillIds);

    List<SkillShare> findBySkillIdAndApprovalStatus(Long skillId, String approvalStatus);

    void deleteBySkillId(Long skillId);
}
