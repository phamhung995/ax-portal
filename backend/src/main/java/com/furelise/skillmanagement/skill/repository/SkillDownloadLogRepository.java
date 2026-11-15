package com.furelise.skillmanagement.skill.repository;

import com.furelise.skillmanagement.skill.model.SkillDownloadLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface SkillDownloadLogRepository extends JpaRepository<SkillDownloadLog, Long> {

    /**
     * "Download All" log rows within a window for a set of skills (chart
     * "total downloads" metric). Only the rows are needed — the caller
     * counts them (excluding the skill creator's own downloads).
     */
    List<SkillDownloadLog> findBySkillIdInAndDownloadedAtBetweenAndDownloadAllTrue(
            Collection<Long> skillIds, Instant start, Instant end);

    /** All download events of a set of skills (grid "Downloads" column). */
    List<SkillDownloadLog> findBySkillIdIn(Collection<Long> skillIds);
}
