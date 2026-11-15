package com.furelise.skillmanagement.skill.repository;

import com.furelise.skillmanagement.skill.model.SkillStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillStatusHistoryRepository extends JpaRepository<SkillStatusHistory, Long> {

    List<SkillStatusHistory> findBySkillIdOrderByChangedAtDesc(Long skillId);
}
