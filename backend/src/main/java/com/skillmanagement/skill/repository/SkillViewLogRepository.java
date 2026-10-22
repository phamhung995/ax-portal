package com.skillmanagement.skill.repository;

import com.skillmanagement.skill.model.SkillViewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SkillViewLogRepository extends JpaRepository<SkillViewLog, Long> {

    /** Latest view entry for a user on a skill (idempotent view-window check). */
    Optional<SkillViewLog> findTopBySkillIdAndUserIdOrderByViewedAtDesc(Long skillId, Long userId);

    /**
     * Daily view counts for a set of skills since a point in time (activity chart).
     * Returns rows of [LocalDate day, long count], bucketed in the given zone
     * (must match the zone the caller uses to build its day indices).
     */
    @Query(value = "SELECT (vl.viewed_at AT TIME ZONE :tz)::date AS d, COUNT(*) "
            + "FROM skill_view_log vl "
            + "WHERE vl.skill_id IN :skillIds AND vl.viewed_at >= :since "
            + "GROUP BY 1", nativeQuery = true)
    List<Object[]> countViewsByDaySince(
            @Param("skillIds") Collection<Long> skillIds,
            @Param("since") Instant since,
            @Param("tz") String tz);
}
