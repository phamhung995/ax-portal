package com.skillmanagement.ainews.controller;

import com.skillmanagement.ainews.batch.AiNewsBatchService;
import com.skillmanagement.ainews.dto.AiNewsBatchLogItem;
import com.skillmanagement.ainews.dto.AiNewsRunResult;
import com.skillmanagement.ainews.model.AiNewsBatchLog;
import com.skillmanagement.ainews.repository.AiNewsBatchLogRepository;
import com.skillmanagement.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI News batch admin endpoints (AI_News_Batch.md §7.1, §7.2).
 * Admin only (ROLE_ADMIN_USER); NORMAL_USER gets 403 (AC-NEWS-08).
 *   POST /api/v1/admin/ai-news/run          — manual run (synchronous, max 10 min)
 *   GET  /api/v1/admin/ai-news/logs?limit=20 — latest run logs
 */
@RestController
@RequestMapping("/api/v1/admin/ai-news")
@RequiredArgsConstructor
@Secured("ROLE_ADMIN_USER")
public class AdminAiNewsController {

    private final AiNewsBatchService batchService;
    private final AiNewsBatchLogRepository batchLogRepository;

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<AiNewsRunResult>> run() {
        AiNewsBatchService.JobRunResult result =
                batchService.runWithTimeout(AiNewsBatchLog.TRIGGER_MANUAL);
        return ResponseEntity.ok(ApiResponse.ok(new AiNewsRunResult(
                result.runId(), result.status(), result.totalInserted(),
                result.totalSkipped(), result.totalFailed(), result.totalArchived())));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<AiNewsBatchLogItem>>> logs(
            @RequestParam(defaultValue = "20") int limit) {
        int capped = Math.min(Math.max(limit, 1), 100);
        List<AiNewsBatchLogItem> items = batchLogRepository
                .findLatest(capped)
                .stream()
                .map(log -> new AiNewsBatchLogItem(
                        log.getRunId(), log.getStartedAt(), log.getFinishedAt(), log.getStatus(),
                        log.getTotalFetched(), log.getTotalInserted(), log.getTotalSkipped(),
                        log.getTotalFailed(), log.getTotalArchived(),
                        log.getErrorMessage(), log.getTriggerType()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(items));
    }
}
