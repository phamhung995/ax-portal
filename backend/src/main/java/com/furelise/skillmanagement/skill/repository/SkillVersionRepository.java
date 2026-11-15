package com.furelise.skillmanagement.skill.repository;

import com.furelise.skillmanagement.skill.model.SkillVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillVersionRepository extends JpaRepository<SkillVersion, Long> {

    List<SkillVersion> findBySkillIdOrderByVersionDesc(Long skillId);
}
