package com.furelise.skillmanagement.ainews;

import com.furelise.skillmanagement.ainews.batch.AiNewsBatchService;
import com.furelise.skillmanagement.ainews.model.AiNewsBatchLog;
import com.furelise.skillmanagement.ainews.model.AiNewsSource;
import com.furelise.skillmanagement.ainews.repository.AiNewsSourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LIVE verification of the Knox portal batch run against the real GDC
 * mailbox (gdcv.cag.ax@samsung.com) on in-memory H2 (no real PostgreSQL).
 *
 * <p>Boots the full app on the {@code local-h2} profile (Flyway off,
 * schema from JPA, cron off), seeds the GDC source row via
 * {@code LocalH2TestSeeder}, supplies the mailbox password from the
 * {@code AI_NEWS_SECRET_GDC} env var (BR-NEWS-011 — never committed),
 * and triggers a manual batch run that logs into the real portal and
 * reads the real inbox.
 *
 * <p>Skipped when {@code AI_NEWS_SECRET_GDC} is not set, so a normal
 * build never hits the portal. Run explicitly:
 * {@code AI_NEWS_SECRET_GDC=... mvnw test -Dtest=AiNewsKnoxLiveBatchTest}</p>
 */
@SpringBootTest
@ActiveProfiles("local-h2")
@EnabledIfEnvironmentVariable(named = "AI_NEWS_SECRET_GDC", matches = ".+")
class AiNewsKnoxLiveBatchTest {

    @Autowired
    private AiNewsBatchService batchService;
    @Autowired
    private AiNewsSourceRepository sourceRepository;

    @DynamicPropertySource
    static void secrets(DynamicPropertyRegistry registry) {
        registry.add("ai-news.mailbox-secrets.gdc-tech-support",
                () -> System.getenv("AI_NEWS_SECRET_GDC"));
    }

    @Test
    @DisplayName("live: batch run logs into the Knox portal and imports the GDC inbox into H2")
    void livePortalRun() {
        AiNewsSource source = sourceRepository
                .findByMailbox("gdcv.cag.ax")
                .orElseThrow(() -> new AssertionError(
                        "GDC source row not seeded (LocalH2TestSeeder)"));
        assertThat(source.getStatus()).isEqualTo(AiNewsSource.STATUS_ACTIVE);

        AiNewsBatchService.JobRunResult result =
                batchService.runWithTimeout(AiNewsBatchLog.TRIGGER_MANUAL);

        System.out.println("[LIVE] runId=" + result.runId()
                + " status=" + result.status()
                + " inserted=" + result.totalInserted()
                + " skipped=" + result.totalSkipped()
                + " failedSources=" + result.totalFailed()
                + " archived=" + result.totalArchived());

        // A login failure surfaces as PARTIAL with the portal error in the
        // batch log (e.g. "Knox portal login failed: NOT_EXIST_ID"). Success
        // is allowed to import 0 emails (empty inbox within the window).
        assertThat(result.status()).isNotEqualTo(AiNewsBatchLog.STATUS_FAILED);
        assertThat(result.totalFailed()).isZero();
        // Re-fetch: the run updates last_success_run_at in its own
        // transaction — the pre-run entity reference is stale.
        AiNewsSource after = sourceRepository
                .findByMailbox("gdcv.cag.ax")
                .orElseThrow(() -> new AssertionError("GDC source row vanished"));
        assertThat(after.getLastSuccessRunAt()).isNotNull();
    }
}
