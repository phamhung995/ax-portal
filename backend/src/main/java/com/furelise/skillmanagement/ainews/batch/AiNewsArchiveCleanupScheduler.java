package com.furelise.skillmanagement.ainews.batch;

import com.furelise.skillmanagement.ainews.model.AiNewsBatchLog;
import com.furelise.skillmanagement.ainews.repository.AiNewsBatchLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * AI News archive cleanup sweep (BR-NEWS-010) — runs independently of the
 * import job so that archiving + image-disk cleanup still progress when the
 * import fails or the portal is unreachable for an extended period.
 *
 * <p>Two sweeps:
 * <ul>
 *   <li><b>Daily (03:30, incremental)</b> — flips the 90-day archive and
 *       deletes image folders of news archived in the last 24h. Bounded
 *       work, independent of the ever-growing archived set.</li>
 *   <li><b>Weekly (Sunday 04:30, full)</b> — deletes image folders of the
 *       ENTIRE archived set. Self-healing: if a run died between the
 *       archive_yn flip and the file deletion, the orphaned files are
 *       removed on the next full sweep (orphan disk leak is bounded to
 *       ~7 days × crash volume).</li>
 * </ul>
 *
 * <p>Also repairs orphaned {@code RUNNING} batch-log rows at startup: a JVM
 * crash mid-run leaves the row RUNNING forever, which (a) shows a permanent
 * "running" row in the admin log UI and (b) masks the BR-NEWS-012
 * consecutive-failure alert (RUNNING is neither FAILED nor PARTIAL, so it
 * breaks the failure chain in the last-3 window).</p>
 *
 * <p>Gated by {@code ai-news.enabled} (default false — batch paused) — the
 * same master switch as the import job, so a local/dev backend never writes
 * to the connected DB.</p>
 */
@Component
public class AiNewsArchiveCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(AiNewsArchiveCleanupScheduler.class);

    private final AiNewsBatchService batchService;
    private final AiNewsBatchProperties properties;
    private final AiNewsBatchLogRepository logRepository;

    public AiNewsArchiveCleanupScheduler(AiNewsBatchService batchService,
                                         AiNewsBatchProperties properties,
                                         AiNewsBatchLogRepository logRepository) {
        this.batchService = batchService;
        this.properties = properties;
        this.logRepository = logRepository;
    }

    /**
     * Daily archive sweep (03:30 — between the two import runs): flip the
     * 90-day archive + delete image folders of news archived in the last
     * 24h (incremental). Best-effort: a failure only logs (the import job's
     * own cleanup step retries too).
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void runArchiveCleanup() {
        if (!properties.isEnabled()) {
            return;
        }
        int archived = batchService.runArchiveCleanup(false);
        if (archived > 0) {
            log.info("AI-NEWS-IMPORT archive sweep archived {} news item(s)", archived);
        }
    }

    /**
     * Weekly FULL image sweep (Sunday 04:30): deletes image folders of the
     * entire archived set — self-healing for orphan folders from a crash
     * between the archive flip and the file delete (see class javadoc). The
     * 90-day archive flip also runs (idempotent — the daily sweep normally
     * handles it; this covers a missed daily sweep).
     */
    @Scheduled(cron = "0 30 4 ? * SUN")
    public void runFullArchiveSweep() {
        if (!properties.isEnabled()) {
            return;
        }
        batchService.runArchiveCleanup(true);
    }

    /**
     * Marks orphaned RUNNING rows as FAILED (the process that started them is
     * gone — this instance just booted). Runs once after the context is up.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void markOrphanedRunningRunsFailed() {
        // Same master switch as the rest of the module: a local/dev instance
        // (ai-news.enabled=false) pointed at a shared DB must not flip a
        // production run's row (the original run re-saves its final status
        // only while it is still alive — a flipped row stays FAILED).
        if (!properties.isEnabled()) {
            return;
        }
        try {
            List<AiNewsBatchLog> running = logRepository.findByStatus(AiNewsBatchLog.STATUS_RUNNING);
            for (AiNewsBatchLog run : running) {
                run.setStatus(AiNewsBatchLog.STATUS_FAILED);
                run.setFinishedAt(Instant.now());
                run.setErrorMessage("Process restarted mid-run — marked FAILED at startup");
                logRepository.save(run);
                log.warn("AI-NEWS-IMPORT marked orphaned RUNNING batch-log row {} as FAILED",
                        run.getRunId());
            }
        } catch (Exception e) {
            log.error("AI-NEWS-IMPORT failed to mark orphaned RUNNING runs: {}", e.getMessage());
        }
    }
}
