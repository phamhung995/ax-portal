package com.furelise.skillmanagement.ainews.batch;

import com.furelise.skillmanagement.ainews.model.AiNewsBatchLog;
import com.furelise.skillmanagement.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * AI-NEWS-IMPORT schedule (AI_News_Batch.md §4.1): 00:00 and 12:00 daily.
 * The service itself holds the concurrency lock (BR-NEWS-008) — a second
 * trigger while a run is in progress is skipped + logged.
 *
 * <p>The cron trigger submits the run to a dedicated daemon worker instead
 * of running it inline: the {@code @Scheduled} pool is single-threaded by
 * default, so an inline run (up to the 10-minute cap, or longer when the
 * portal hangs between deadline checks) would block every other scheduled
 * task in the app (token cleanup 02:00, archive sweep 03:30). The
 * wall-clock deadline inside {@code doRun} still bounds the run, and the
 * service's in-memory lock still guards concurrency (a queued trigger is
 * skipped + logged by the service).
 *
 * <p>Gated by {@code ai-news.enabled} (default false — batch paused): local/dev
 * setups keep it false so a locally running backend never fires the cron and
 * writes to the connected DB.
 */
@Component
@EnableConfigurationProperties(AiNewsBatchProperties.class)
public class AiNewsBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(AiNewsBatchScheduler.class);

    private final AiNewsBatchService batchService;
    private final AiNewsBatchProperties properties;

    /**
     * Single daemon worker for the cron run (see the class javadoc for why
     * the run must not execute on the scheduler thread). ThreadPoolExecutor
     * (not ExecutorService) so the trigger can check the queue depth.
     */
    private final ThreadPoolExecutor cronWorker = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "ai-news-import-cron");
                t.setDaemon(true);
                return t;
            });

    public AiNewsBatchScheduler(AiNewsBatchService batchService, AiNewsBatchProperties properties) {
        this.batchService = batchService;
        this.properties = properties;
    }

    @Scheduled(cron = "${ai-news.cron:0 0 0,12 * * ?}")
    public void runScheduled() {
        if (!properties.isEnabled()) {
            log.info("AI-NEWS-IMPORT cron skipped: ai-news.enabled=false (local/dev mode)");
            return;
        }
        // A previous run is still queued (pathological hang) — do not grow
        // the queue; the in-service lock would skip the queued run anyway.
        if (!cronWorker.getQueue().isEmpty()) {
            log.warn("AI-NEWS-IMPORT cron trigger skipped: a previous run is still queued");
            return;
        }
        cronWorker.execute(() -> {
            try {
                batchService.run(AiNewsBatchLog.TRIGGER_CRON);
            } catch (BusinessException e) {
                // NEWS_JOB_RUNNING (concurrent manual run) — expected, skip.
                log.warn("AI-NEWS-IMPORT cron run skipped: {}", e.getMessage());
            } catch (Exception e) {
                log.error("AI-NEWS-IMPORT cron run failed: {}", e.getMessage(), e);
            }
        });
    }
}
