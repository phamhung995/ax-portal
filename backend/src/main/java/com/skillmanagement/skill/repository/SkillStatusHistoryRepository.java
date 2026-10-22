package com.skillmanagement.skill.repository;

import com.skillmanagement.skill.model.SkillStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillStatusHistoryRepository extends JpaRepository<SkillStatusHistory, Long> {

    List<SkillStatusHistory> findBySkillIdOrderByChangedAtDesc(Long skillId);
}
