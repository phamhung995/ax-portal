package com.skillmanagement.ainews.batch;

import com.skillmanagement.ainews.model.AiNewsBatchLog;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI-NEWS-IMPORT schedule (AI_News_Batch.md §4.1): 00:00 and 12:00 daily.
 * The service itself holds the concurrency lock (BR-NEWS-008) — a second
 * trigger while a run is in progress is skipped + logged.
 */
@Component
@EnableConfigurationProperties(AiNewsBatchProperties.class)
public class AiNewsBatchScheduler {

    private final AiNewsBatchService batchService;

    public AiNewsBatchScheduler(AiNewsBatchService batchService) {
        this.batchService = batchService;
    }

    @Scheduled(cron = "${ai-news.cron:0 0 0,12 * * ?}")
    public void runScheduled() {
        batchService.run(AiNewsBatchLog.TRIGGER_CRON);
    }
}
