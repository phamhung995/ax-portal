package com.skillmanagement.skill.repository;

import com.skillmanagement.skill.model.SkillSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillSubscriptionRepository
        extends JpaRepository<SkillSubscription, SkillSubscription.SkillSubscriptionId> {

    boolean existsBySkillIdAndUserId(Long skillId, Long userId);

    List<SkillSubscription> findBySkillId(Long skillId);

    List<SkillSubscription> findByUserId(Long userId);

    void deleteBySkillIdAndUserId(Long skillId, Long userId);

    void deleteBySkillId(Long skillId);

    long countByUserId(Long userId);
}
