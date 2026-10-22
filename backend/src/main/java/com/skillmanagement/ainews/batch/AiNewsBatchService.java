package com.skillmanagement.ainews.batch;

import com.skillmanagement.ainews.email.EmailReader;
import com.skillmanagement.ainews.email.EmailReaderException;
import com.skillmanagement.ainews.email.EmailRecord;
import com.skillmanagement.ainews.email.HtmlSanitizer;
import com.skillmanagement.ainews.email.MailboxSecretProvider;
import com.skillmanagement.ainews.model.AiNews;
import com.skillmanagement.ainews.model.AiNewsBatchLog;
import com.skillmanagement.ainews.model.AiNewsSource;
import com.skillmanagement.ainews.repository.AiNewsBatchLogRepository;
import com.skillmanagement.ainews.repository.AiNewsRepository;
import com.skillmanagement.ainews.repository.AiNewsSourceRepository;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.exception.BusinessErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI-NEWS-IMPORT batch job (AI_News_Batch.md §4.2).
 *
 * <p>Flow: lock → read ACTIVE sources → per source: fetch emails (since =
 * last_success_run_at ?? now-2d, limit 50) → filter/parse/sanitize/dedup →
 * INSERT (1 transaction per source) → update last_success_run_at (2-branch
 * limit logic, BR-NEWS-009) → archive cleanup (BR-NEWS-010, best-effort) →
 * write ai_news_batch_log → alert on 3 consecutive FAILED/PARTIAL runs
 * (BR-NEWS-012).
 *
 * <p>One failing source does NOT fail the whole job (BR-NEWS-006); its
 * last_success_run_at is left untouched so its emails are re-fetched next run.
 *
 * <p>Runtime is naturally bounded: IMAP READONLY with a 60s timeout per
 * mailbox (6 sources × 50 emails ≪ the 10-minute cap, NFR-NEWS-01).
 */
@Service
@Slf4j
public class AiNewsBatchService {

    static final int TITLE_MAX = 200;
    static final int MESSAGE_ID_MAX = 320;
    static final Duration MAX_RUN = Duration.ofMinutes(10);
    static final int CONSECUTIVE_FAIL_ALERT = 3;

    private final AiNewsSourceRepository sourceRepository;
    private final AiNewsRepository newsRepository;
    private final AiNewsBatchLogRepository logRepository;
    private final EmailReader emailReader;
    private final HtmlSanitizer htmlSanitizer;
    private final MailboxSecretProvider secretProvider;
    private final AiNewsBatchProperties properties;
    private final TransactionTemplate sourceTransaction;

    /** Concurrency guard (BR-NEWS-008) — a second trigger is skipped + logged. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Single daemon worker for manual runs with the 10-minute cap (§4.1). */
    private final ExecutorService manualWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-news-import-manual");
        t.setDaemon(true);
        return t;
    });

    public AiNewsBatchService(AiNewsSourceRepository sourceRepository,
                              AiNewsRepository newsRepository,
                              AiNewsBatchLogRepository logRepository,
                              EmailReader emailReader,
                              HtmlSanitizer htmlSanitizer,
                              MailboxSecretProvider secretProvider,
                              AiNewsBatchProperties properties,
                              PlatformTransactionManager transactionManager) {
        this.sourceRepository = sourceRepository;
        this.newsRepository = newsRepository;
        this.logRepository = logRepository;
        this.emailReader = emailReader;
        this.htmlSanitizer = htmlSanitizer;
        this.secretProvider = secretProvider;
        this.properties = properties;
        this.sourceTransaction = new TransactionTemplate(transactionManager);
        this.sourceTransaction.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Runs the import job (cron trigger — executes inline).
     *
     * @param triggerType CRON or MANUAL
     * @return the finished run summary
     * @throws BusinessException NEWS_JOB_RUNNING when a run is already in progress
     */
    public JobRunResult run(String triggerType) {
        if (!running.compareAndSet(false, true)) {
            log.warn("AI-NEWS-IMPORT already running — skipping trigger ({})", triggerType);
            throw new BusinessException(BusinessErrorCode.NEWS_JOB_RUNNING,
                    "AI news import job is already running");
        }
        try {
            return doRun(triggerType);
        } finally {
            running.set(false);
        }
    }

    /**
     * Runs the import job with the hard 10-minute cap (§4.1) — used by the
     * admin manual-run endpoint. If the worker exceeds the cap it is
     * interrupted; the worker's own flow still writes the final log row.
     */
    public JobRunResult runWithTimeout(String triggerType) {
        CompletableFuture<JobRunResult> future = CompletableFuture.supplyAsync(
                () -> run(triggerType), manualWorker);
        try {
            return future.get(MAX_RUN.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("AI-NEWS-IMPORT exceeded {} — interrupted (FAILED/timeout)", MAX_RUN);
            throw new BusinessException(BusinessErrorCode.NEWS_JOB_FAILED,
                    "AI news import job timed out after " + MAX_RUN.toMinutes() + " minutes");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new BusinessException(BusinessErrorCode.NEWS_JOB_FAILED,
                    "AI news import job was interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(BusinessErrorCode.NEWS_JOB_FAILED,
                    "AI news import job failed: " + cause.getMessage());
        }
    }

    private JobRunResult doRun(String triggerType) {
        Instant startedAt = Instant.now();
        AiNewsBatchLog runLog = logRepository.save(AiNewsBatchLog.builder()
                .startedAt(startedAt)
                .status(AiNewsBatchLog.STATUS_RUNNING)
                .triggerType(triggerType)
                .build());

        int fetched = 0;
        int inserted = 0;
        int skipped = 0;
        int failedSources = 0;
        int archived = 0;
        String errorMessage = null;
        boolean fatal = false;

        try {
            List<AiNewsSource> sources =
                    sourceRepository.findByStatus(AiNewsSource.STATUS_ACTIVE);
            for (AiNewsSource source : sources) {
                try {
                    SourceResult result = processSource(source);
                    fetched += result.fetched();
                    inserted += result.inserted();
                    skipped += result.skipped();
                } catch (Exception e) {
                    // BR-NEWS-006: one failing source must not stop the others.
                    failedSources++;
                    errorMessage = "source " + source.getMailbox() + " failed: " + e.getMessage();
                    log.error("AI-NEWS-IMPORT source {} failed: {}", source.getMailbox(), e.getMessage());
                }
            }

            // Step 6 — archive cleanup (BR-NEWS-010), best-effort: a failure here
            // does NOT change the job status (import already succeeded).
            try {
                archived = newsRepository.archiveOlderThan(
                        Instant.now().minus(Duration.ofDays(properties.getArchiveDays())));
            } catch (Exception e) {
                log.error("AI-NEWS-IMPORT archive cleanup failed (will retry next run): {}",
                        e.getMessage());
            }
        } catch (Exception e) {
            // Fatal (e.g. DB down) — the whole run fails.
            fatal = true;
            errorMessage = e.getMessage();
            log.error("AI-NEWS-IMPORT failed: {}", e.getMessage(), e);
        }

        String status = fatal
                ? AiNewsBatchLog.STATUS_FAILED
                : failedSources > 0
                        ? AiNewsBatchLog.STATUS_PARTIAL
                        : AiNewsBatchLog.STATUS_SUCCESS;

        runLog.setStatus(status);
        runLog.setFinishedAt(Instant.now());
        runLog.setTotalFetched(fetched);
        runLog.setTotalInserted(inserted);
        runLog.setTotalSkipped(skipped);
        runLog.setTotalFailed(failedSources);
        runLog.setTotalArchived(archived);
        runLog.setErrorMessage(errorMessage);
        logRepository.save(runLog);

        alertIfConsecutiveFailures();

        return new JobRunResult(runLog.getRunId(), status, inserted, skipped, failedSources, archived);
    }

    /**
     * Fetches + imports one source (1 transaction per source, §4.2 step 4d)
     * and updates last_success_run_at on success (BR-NEWS-007).
     */
    private SourceResult processSource(AiNewsSource source) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(Duration.ofDays(properties.getImportWindowDays()));
        Instant since = source.getLastSuccessRunAt() != null
                ? source.getLastSuccessRunAt()
                : windowStart;
        String password = secretProvider.resolve(source.getSecretRef());
        String imapHost = resolveImapHost(source.getImapHost());

        List<EmailRecord> emails = emailReader.read(
                imapHost,
                source.getImapPort() != null ? source.getImapPort().intValue() : 993,
                source.getMailbox(),
                password,
                "INBOX",
                since,
                properties.getFetchLimit());

        int fetched = 0;
        AtomicInteger inserted = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        // BR-NEWS-009: newest date among ALL fetched emails (the boundary for
        // last_success_run_at when the fetch hits the limit). Must cover every
        // fetched email — including ones later dedup-skipped — otherwise a full
        // page of duplicates would leave the marker at `now` and drop the emails
        // beyond the 50.
        AtomicReference<Instant> maxFetchedAt = new AtomicReference<>();
        for (EmailRecord email : emails) {
            fetched++;
            if (email.date() != null) {
                maxFetchedAt.updateAndGet(cur ->
                        cur == null || email.date().isAfter(cur) ? email.date() : cur);
            }
            // §3.3 rule 2: skip emails older than the 2-day window.
            if (email.date() == null || email.date().isBefore(windowStart)) {
                skipped.incrementAndGet();
                continue;
            }
            // §3.3 rule 3: skip auto-replies and empty subjects.
            if (email.isAutoReply() || email.subject() == null || email.subject().isBlank()) {
                skipped.incrementAndGet();
                continue;
            }

            final String title = cleanTitle(email.subject());
            final String body = htmlSanitizer.sanitize(email.bodyHtml() != null
                    ? email.bodyHtml()
                    : email.bodyText());
            final String rawMessageId = email.messageId();
            final boolean fallback = rawMessageId == null || rawMessageId.isBlank();
            final String baseMessageId = fallback
                    ? sha256(safe(email.from()) + "|" + email.date() + "|" + safe(email.subject()))
                    : rawMessageId.trim();
            final Instant createdAt = email.date();
            final String sender = email.from();
            final String sourceName = source.getDisplayName();

            // 1 transaction per source — dedup + insert + marker update together.
            sourceTransaction.executeWithoutResult(status -> {
                if (newsRepository.existsByMessageId(baseMessageId)) {
                    // Real Message-ID already imported → dedup skip (BR-NEWS-002).
                    // Fallback hash collision → try -1, -2, ... (§4.3).
                    if (!fallback) {
                        skipped.incrementAndGet();
                        return;
                    }
                    int suffix = 1;
                    String candidate = baseMessageId + "-" + suffix;
                    while (newsRepository.existsByMessageId(candidate)) {
                        candidate = baseMessageId + "-" + (++suffix);
                    }
                    insertNews(candidate, title, body, sender, sourceName, createdAt, inserted);
                    return;
                }
                insertNews(baseMessageId, title, body, sender, sourceName, createdAt, inserted);
            });
        }

        // BR-NEWS-009: fetch < limit → now(); fetch == limit → MAX(created_at) of
        // all FETCHED emails − 1s (not just imported ones — see maxFetchedAt).
        Instant marker = fetched < properties.getFetchLimit()
                ? now
                : (maxFetchedAt.get() != null ? maxFetchedAt.get().minusSeconds(1) : now);
        source.setLastSuccessRunAt(marker);
        source.setUpdatedAt(Instant.now());
        sourceRepository.save(source);

        return new SourceResult(fetched, inserted.get(), skipped.get());
    }

    /**
     * Resolves the IMAP host for a source. Sources seeded with the
     * placeholder {@code imap.internal} (V27) fall back to the configured
     * {@code ai-news.default-imap-host} — the real host is environment
     * specific and supplied via env var, never hardcoded in the migration.
     */
    private String resolveImapHost(String configuredHost) {
        if (configuredHost != null && !configuredHost.isBlank()
                && !"imap.internal".equals(configuredHost)) {
            return configuredHost;
        }
        String fallback = properties.getDefaultImapHost();
        if (fallback == null || fallback.isBlank()) {
            throw new EmailReaderException(
                    "No IMAP host configured (ai-news.default-imap-host is empty) "
                            + "and source host is still the placeholder 'imap.internal'");
        }
        return fallback;
    }

    /**
     * BR-NEWS-012: FAILED/PARTIAL 3 consecutive runs → alert Ops (log alert).
     */
    private void alertIfConsecutiveFailures() {
        List<AiNewsBatchLog> recent = logRepository.findTop3ByOrderByRunIdDesc();
        if (recent.size() < CONSECUTIVE_FAIL_ALERT) {
            return;
        }
        boolean allFailing = recent.stream().allMatch(l ->
                AiNewsBatchLog.STATUS_FAILED.equals(l.getStatus())
                        || AiNewsBatchLog.STATUS_PARTIAL.equals(l.getStatus()));
        if (allFailing) {
            log.error("ALERT: AI-NEWS-IMPORT FAILED/PARTIAL {} consecutive runs — notify Ops "
                    + "(BR-NEWS-012). Last error: {}", recent.size(),
                    recent.get(0).getErrorMessage());
        }
    }

    private void insertNews(String messageId, String title, String body, String sender,
                            String sourceName, Instant createdAt,
                            AtomicInteger inserted) {
        String id = messageId.length() > MESSAGE_ID_MAX
                ? messageId.substring(0, MESSAGE_ID_MAX)
                : messageId;
        newsRepository.save(AiNews.builder()
                .messageId(id)
                .title(title)
                .body(body)
                .sender(sender)
                .source(sourceName)
                .createdAt(createdAt)
                .build());
        inserted.incrementAndGet();
    }

    /** Title = subject, leading bracketed prefixes ([ANN], [GUIDE]...) stripped, truncated to 200. */
    static String cleanTitle(String subject) {
        String title = subject.trim();
        while (title.startsWith("[")) {
            int end = title.indexOf(']');
            if (end < 0) {
                break;
            }
            title = title.substring(end + 1).trim();
        }
        if (title.isBlank()) {
            title = subject.trim();
        }
        return title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX) : title;
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** Per-source outcome of one import pass. */
    private record SourceResult(int fetched, int inserted, int skipped) {
    }

    /** Summary returned by a finished run (AI_News_Batch.md §7.1 response). */
    public record JobRunResult(
            Long runId,
            String status,
            int totalInserted,
            int totalSkipped,
            int totalFailed,
            int totalArchived
    ) {
    }
}
