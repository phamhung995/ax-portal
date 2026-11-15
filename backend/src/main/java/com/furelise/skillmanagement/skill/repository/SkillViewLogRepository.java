package com.furelise.skillmanagement.skill.repository;

import com.furelise.skillmanagement.skill.model.SkillViewLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SkillViewLogRepository extends JpaRepository<SkillViewLog, Long> {

    /** Latest view entry for a user on a skill (idempotent view-window check). */
    Optional<SkillViewLog> findTopBySkillIdAndUserIdOrderByViewedAtDesc(Long skillId, Long userId);

    /**
     * View-log rows within a window for a set of skills (chart "total views"
     * metric). Only the rows are needed — the caller counts them.
     */
    List<SkillViewLog> findBySkillIdInAndViewedAtBetween(
            Collection<Long> skillIds, Instant start, Instant end);
}
