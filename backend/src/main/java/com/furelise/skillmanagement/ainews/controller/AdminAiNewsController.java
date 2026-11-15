package com.furelise.skillmanagement.ainews.controller;

import com.furelise.skillmanagement.ainews.batch.AiNewsBatchService;
import com.furelise.skillmanagement.ainews.dto.AiNewsBatchLogItem;
import com.furelise.skillmanagement.ainews.dto.AiNewsRunResult;
import com.furelise.skillmanagement.ainews.dto.MhtImportResult;
import com.furelise.skillmanagement.ainews.model.AiNewsBatchLog;
import com.furelise.skillmanagement.ainews.repository.AiNewsBatchLogRepository;
import com.furelise.skillmanagement.ainews.service.AiNewsMhtImportService;
import com.furelise.skillmanagement.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AI News batch admin endpoints (AI_News_Batch.md §7.1, §7.2) + manual
 * .mht import (AI_News_Screen_SRS.md v1.14 §5.7).
 * Admin only (ROLE_ADMIN_USER); NORMAL_USER gets 403 (AC-NEWS-08).
 *   POST /api/v1/admin/ai-news/run          — manual run (synchronous, max 10 min)
 *   GET  /api/v1/admin/ai-news/logs?limit=20 — latest run logs
 *   POST /api/v1/admin/ai-news/import       — manual .mht import (multipart files[])
 */
@RestController
@RequestMapping("/api/v1/admin/ai-news")
@RequiredArgsConstructor
@Secured("ROLE_ADMIN_USER")
public class AdminAiNewsController {

    private final AiNewsBatchService batchService;
    private final AiNewsBatchLogRepository batchLogRepository;
    private final AiNewsMhtImportService mhtImportService;

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

    /**
     * Manual .mht import (AI_News_Screen_SRS.md v1.14 §5.7).
     * Multipart: {@code files} = 1..20 .mht files. One result per file
     * (INSERTED / SKIPPED_DUPLICATE / FAILED) — a failing file does not
     * stop the others.
     */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<List<MhtImportResult>>> importMht(
            @RequestParam("files") MultipartFile[] files) {
        return ResponseEntity.ok(ApiResponse.ok(mhtImportService.importFiles(List.of(files))));
    }
}
