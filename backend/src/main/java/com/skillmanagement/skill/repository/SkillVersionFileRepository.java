package com.skillmanagement.skill.repository;

import com.skillmanagement.skill.model.SkillVersionFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillVersionFileRepository extends JpaRepository<SkillVersionFile, Long> {

    List<SkillVersionFile> findBySkillVersionId(Long skillVersionId);
}
