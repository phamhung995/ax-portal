package com.furelise.skillmanagement.ainews.repository;

import com.furelise.skillmanagement.ainews.model.AiNewsBatchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiNewsBatchLogRepository extends JpaRepository<AiNewsBatchLog, Long> {

    /** Latest runs, newest first (admin log endpoint, AI_News_Batch.md §7.2). */
    @Query(value = "SELECT * FROM ai_news_batch_log ORDER BY run_id DESC LIMIT :limit",
            nativeQuery = true)
    List<AiNewsBatchLog> findLatest(@Param("limit") int limit);

    /**
     * Latest 3 NON-SKIPPED, NON-RUNNING runs for the consecutive-failure
     * alert (BR-NEWS-012). SKIPPED rows (concurrent triggers rejected by the
     * lock) must not break the failure chain — otherwise an admin "Run" click
     * during a cron run can mask 3 genuinely consecutive failing runs.
     * RUNNING is excluded too: a row stuck in RUNNING (e.g. a crash before
     * the startup repair) would otherwise mask the alert chain.
     */
    @Query(value = "SELECT * FROM ai_news_batch_log WHERE status NOT IN ('SKIPPED', 'RUNNING') "
            + "ORDER BY run_id DESC LIMIT 3",
            nativeQuery = true)
    List<AiNewsBatchLog> findTop3NonSkippedByOrderByRunIdDesc();

    /** Runs still marked RUNNING (orphaned by a crash — startup repair). */
    List<AiNewsBatchLog> findByStatus(String status);
}
