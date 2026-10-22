package com.skillmanagement.skill.repository;

import com.skillmanagement.skill.model.SkillFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SkillFileRepository extends JpaRepository<SkillFile, Long> {

    List<SkillFile> findBySkillId(Long skillId);

    List<SkillFile> findBySkillIdIn(Collection<Long> skillIds);

    /** Live files of a skill (excludes pending version-update uploads). */
    List<SkillFile> findBySkillIdAndPendingYn(Long skillId, String pendingYn);

    void deleteBySkillId(Long skillId);
}
