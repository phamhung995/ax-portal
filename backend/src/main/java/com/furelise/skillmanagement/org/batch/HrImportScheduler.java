package com.furelise.skillmanagement.org.batch;

import com.furelise.skillmanagement.org.service.impl.HrImportJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * HR-IMPORT schedule: 03:00 daily (after the Organization/Systems import).
 * Runs the newest PENDING upload if any; no-op otherwise.
 *
 * <p>Gated by {@code hr-import.enabled} (default true): local/dev setups
 * can set it to false so a locally running backend never fires the cron.
 */
@Component
public class HrImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(HrImportScheduler.class);

    private final HrImportJobService jobService;
    private final boolean enabled;

    public HrImportScheduler(HrImportJobService jobService,
                             @Value("${hr-import.enabled:true}") boolean enabled) {
        this.jobService = jobService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${hr-import.cron:0 0 3 * * ?}")
    public void runScheduled() {
        if (!enabled) {
            log.info("HR-IMPORT cron skipped: hr-import.enabled=false (local/dev mode)");
            return;
        }
        jobService.runPendingImport("CRON");
    }
}
