package com.furelise.skillmanagement.skill.repository;

import com.furelise.skillmanagement.skill.model.SkillCoworkUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Repository for skill_cowork_users.
 */
public interface SkillCoworkUserRepository
        extends JpaRepository<SkillCoworkUser, SkillCoworkUser.SkillCoworkUserKey> {

    List<SkillCoworkUser> findBySkillId(Long skillId);

    List<SkillCoworkUser> findBySkillIdIn(Collection<Long> skillIds);

    void deleteBySkillId(Long skillId);

    boolean existsBySkillIdAndUserId(Long skillId, Long userId);
}
