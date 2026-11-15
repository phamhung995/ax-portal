package com.furelise.skillmanagement.org.batch;

import com.furelise.skillmanagement.org.service.impl.OrgImportJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ORG-IMPORT schedule: 02:00 daily (same time as the Systems import).
 * Runs the newest PENDING upload if any; no-op otherwise.
 *
 * <p>Gated by {@code org-import.enabled} (default true): local/dev setups
 * can set it to false so a locally running backend never fires the cron.
 */
@Component
public class OrgImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrgImportScheduler.class);

    private final OrgImportJobService jobService;
    private final boolean enabled;

    public OrgImportScheduler(OrgImportJobService jobService,
                              @Value("${org-import.enabled:true}") boolean enabled) {
        this.jobService = jobService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${org-import.cron:0 0 2 * * ?}")
    public void runScheduled() {
        if (!enabled) {
            log.info("ORG-IMPORT cron skipped: org-import.enabled=false (local/dev mode)");
            return;
        }
        jobService.runPendingImport("CRON");
    }
}
