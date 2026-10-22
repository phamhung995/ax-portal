package com.skillmanagement.ainews.repository;

import com.skillmanagement.ainews.model.AiNewsBatchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiNewsBatchLogRepository extends JpaRepository<AiNewsBatchLog, Long> {

    /** Latest runs, newest first (admin log endpoint, AI_News_Batch.md §7.2). */
    @Query(value = "SELECT * FROM ai_news_batch_log ORDER BY run_id DESC LIMIT :limit",
            nativeQuery = true)
    List<AiNewsBatchLog> findLatest(@Param("limit") int limit);

    /** Latest 3 runs for the consecutive-failure alert (BR-NEWS-012). */
    @Query(value = "SELECT * FROM ai_news_batch_log ORDER BY run_id DESC LIMIT 3",
            nativeQuery = true)
    List<AiNewsBatchLog> findTop3ByOrderByRunIdDesc();
}
