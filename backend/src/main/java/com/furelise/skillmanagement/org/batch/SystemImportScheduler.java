package com.furelise.skillmanagement.org.batch;

import com.furelise.skillmanagement.org.service.impl.SystemImportJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SYSTEMS-IMPORT schedule: 02:00 daily (same time as the Organization import).
 * Runs the newest PENDING upload if any; no-op otherwise.
 *
 * <p>Gated by {@code system-import.enabled} (default true): local/dev setups
 * can set it to false so a locally running backend never fires the cron.
 */
@Component
public class SystemImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(SystemImportScheduler.class);

    private final SystemImportJobService jobService;
    private final boolean enabled;

    public SystemImportScheduler(SystemImportJobService jobService,
                                 @Value("${system-import.enabled:true}") boolean enabled) {
        this.jobService = jobService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${system-import.cron:0 0 2 * * ?}")
    public void runScheduled() {
        if (!enabled) {
            log.info("SYSTEMS-IMPORT cron skipped: system-import.enabled=false (local/dev mode)");
            return;
        }
        jobService.runPendingImport("CRON");
    }
}
