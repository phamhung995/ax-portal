package com.furelise.skillmanagement.ainews.batch;

import com.furelise.skillmanagement.ainews.email.EmailReader;
import com.furelise.skillmanagement.ainews.email.EmailReaderException;
import com.furelise.skillmanagement.ainews.email.EmailRecord;
import com.furelise.skillmanagement.ainews.email.HtmlSanitizer;
import com.furelise.skillmanagement.ainews.email.MailboxSecretProvider;
import com.furelise.skillmanagement.ainews.image.AiNewsImageStore;
import com.furelise.skillmanagement.ainews.model.AiNews;
import com.furelise.skillmanagement.ainews.model.AiNewsBatchLog;
import com.furelise.skillmanagement.ainews.model.AiNewsSource;
import com.furelise.skillmanagement.ainews.repository.AiNewsBatchLogRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsSourceRepository;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.mail.EmailService;
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
import java.util.Map;
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
 * INSERT (short transaction per email — image I/O runs OUTSIDE the
 * transaction) → update last_success_run_at (2-branch limit logic,
 * BR-NEWS-009) → archive cleanup (BR-NEWS-010, best-effort) → write
 * ai_news_batch_log → alert on 3 consecutive FAILED/PARTIAL runs
 * (BR-NEWS-012).
 *
 * <p>One failing source does NOT fail the whole job (BR-NEWS-006); its
 * last_success_run_at is left untouched so its emails are re-fetched next run.
 *
 * <p>Runtime cap (§4.1, NFR-NEWS-01): a wall-clock deadline of
 * {@link #MAX_RUN} is enforced INSIDE {@link #doRun} (checked between
 * sources, between emails, and before image downloads) for BOTH the cron and
 * the manual trigger — a run that exceeds it stops and is logged FAILED.
 * The manual path additionally interrupts the worker thread when the HTTP
 * caller gives up, so the zombie run fails fast on the next portal/image call.
 *
 * <p><b>Single-instance constraint:</b> the concurrency lock
 * ({@code AtomicBoolean running}) is in-memory — it guards ONE JVM only.
 * The deployment runs a single backend instance (docker-compose, 1
 * container). If the app is ever scaled to 2+ backend replicas, two
 * instances could run the import concurrently: the message_id UNIQUE
 * constraint still prevents duplicate rows, but the run wastes a full
 * fetch/parse pass and the two runs overwrite each other's
 * last_success_run_at marker (the marker may lag 1-2 emails — re-fetched
 * next run, dedup absorbs it). Before scaling, replace the in-memory lock
 * with a distributed lock (e.g. {@code pg_try_advisory_lock}) or set
 * {@code AI_NEWS_ENABLED=false} on all but one replica.
 */
@Service
@Slf4j
public class AiNewsBatchService {

    public static final int TITLE_MAX = 200;
    public static final int MESSAGE_ID_MAX = 320;
    /**
     * Cap on the stored body length. The portal's HTML body is
     * attacker-controlled and unbounded; a single pathological email must not
     * bloat the DB row (returned in every detail API response).
     */
    public static final int BODY_MAX = 1_000_000;
    static final Duration MAX_RUN = Duration.ofMinutes(10);
    static final int CONSECUTIVE_FAIL_ALERT = 3;

    private final AiNewsSourceRepository sourceRepository;
    private final AiNewsRepository newsRepository;
    private final AiNewsBatchLogRepository logRepository;
    private final EmailReader emailReader;
    private final HtmlSanitizer htmlSanitizer;
    private final MailboxSecretProvider secretProvider;
    private final AiNewsImageStore imageStore;
    private final AiNewsBatchProperties properties;
    private final EmailService emailService;
    private final TransactionTemplate sourceTransaction;

    /** Concurrency guard (BR-NEWS-008) — a second trigger is skipped + logged.
     *  In-memory: valid for a single backend instance only (see class
     *  javadoc, "Single-instance constraint"). */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Single daemon worker for manual runs with the 10-minute cap (§4.1). */
    private final ExecutorService manualWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-news-import-manual");
        t.setDaemon(true);
        return t;
    });

    /** In-flight manual task — a new manual run is rejected immediately
     *  instead of queueing behind a stuck worker (which would burn the full
     *  10-minute cap per attempt before failing). A timed-out run that is
     *  still executing (zombie) keeps holding this slot until it actually
     *  finishes, so subsequent manual runs fail fast too. */
    private final AtomicReference<CompletableFuture<JobRunResult>> manualTask = new AtomicReference<>();

    public AiNewsBatchService(AiNewsSourceRepository sourceRepository,
                              AiNewsRepository newsRepository,
                              AiNewsBatchLogRepository logRepository,
                              EmailReader emailReader,
                              HtmlSanitizer htmlSanitizer,
                              MailboxSecretProvider secretProvider,
                              AiNewsImageStore imageStore,
                              AiNewsBatchProperties properties,
                              EmailService emailService,
                              PlatformTransactionManager transactionManager) {
        this.sourceRepository = sourceRepository;
        this.newsRepository = newsRepository;
        this.logRepository = logRepository;
        this.emailReader = emailReader;
        this.htmlSanitizer = htmlSanitizer;
        this.secretProvider = secretProvider;
        this.imageStore = imageStore;
        this.properties = properties;
        this.emailService = emailService;
        this.sourceTransaction = new TransactionTemplate(transactionManager);
        this.sourceTransaction.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Runs the import job (cron trigger — executes inline). The wall-clock
     * deadline inside {@link #doRun} bounds the run for both triggers.
     *
     * @param triggerType CRON or MANUAL
     * @return the finished run summary
     * @throws BusinessException NEWS_JOB_RUNNING when a run is already in progress
     */
    public JobRunResult run(String triggerType) {
        if (!running.compareAndSet(false, true)) {
            log.warn("AI-NEWS-IMPORT already running — skipping trigger ({})", triggerType);
            // NFR-NEWS-06: every run (incl. a skip) leaves a log row.
            recordSkippedRun(triggerType);
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
     * Records a SKIPPED run (NFR-NEWS-06 — a concurrent trigger that is
     * rejected by the lock still leaves a log row, §8 "Chạy trùng").
     * Best-effort: a failure here must not mask the NEWS_JOB_RUNNING signal.
     */
    private void recordSkippedRun(String triggerType) {
        try {
            logRepository.save(AiNewsBatchLog.builder()
                    .startedAt(Instant.now())
                    .finishedAt(Instant.now())
                    .status(AiNewsBatchLog.STATUS_SKIPPED)
                    .triggerType(triggerType)
                    .errorMessage("Job already running — trigger skipped (BR-NEWS-008)")
                    .build());
        } catch (Exception e) {
            log.error("AI-NEWS-IMPORT failed to record SKIPPED run: {}", e.getMessage());
        }
    }

    /**
     * Runs the import job with the hard 10-minute cap (§4.1) — used by the
     * admin manual-run endpoint. The worker is interrupted when the caller
     * times out so the job actually stops (the wall-clock deadline inside
     * {@link #doRun} is the backstop — the interrupt makes it stop fast).
     */
    public JobRunResult runWithTimeout(String triggerType) {
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        CompletableFuture<JobRunResult> future = CompletableFuture.supplyAsync(() -> {
            workerThread.set(Thread.currentThread());
            return run(triggerType);
        }, manualWorker);
        CompletableFuture<JobRunResult> previous = manualTask.getAndSet(future);
        if (previous != null && !previous.isDone()) {
            // A previous manual run is still in flight (possibly a timed-out
            // zombie) — reject immediately instead of queueing behind it.
            future.cancel(true);
            // Restore the in-flight task so the NEXT manual run is also
            // rejected fast (otherwise nulling it lets a 3rd run queue).
            manualTask.compareAndSet(future, previous);
            throw new BusinessException(BusinessErrorCode.NEWS_JOB_RUNNING,
                    "AI news import job is already running");
        }
        try {
            return future.get(MAX_RUN.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // CompletableFuture.cancel(true) does NOT interrupt the worker
            // thread (JDK implementation) — interrupt it explicitly. The
            // worker's portal/image calls surface the interrupt as a fast
            // failure; the wall-clock deadline is the backstop.
            Thread t = workerThread.get();
            if (t != null) {
                t.interrupt();
            }
            log.error("AI-NEWS-IMPORT exceeded {} — worker interrupted (FAILED/timeout)", MAX_RUN);
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
        } finally {
            if (future.isDone()) {
                manualTask.compareAndSet(future, null);
            } else {
                // Timed-out zombie still executing — keep it holding the slot
                // (subsequent manual runs are rejected fast) and release it
                // when it actually finishes.
                future.whenComplete((r, ex) -> manualTask.compareAndSet(future, null));
            }
        }
    }

    /** Thrown inside doRun when the wall-clock deadline is exceeded. */
    private static final class DeadlineExceeded extends RuntimeException {
        DeadlineExceeded(String message) {
            super(message);
        }
    }

    /**
     * Step 6 — archive cleanup (BR-NEWS-010), best-effort: a failure here
     * does NOT change the job status (import already succeeded). Also run
     * standalone by {@link AiNewsArchiveCleanupScheduler} so disk + archive
     * progress does not depend on the import run succeeding.
     *
     * <p>The image sweep is INCREMENTAL by default: it only deletes the
     * folders of news archived in the last 24h (bounded work, independent
     * of the ever-growing archived set). {@code fullSweep = true} (weekly)
     * sweeps the ENTIRE archived set — self-healing: if a run died between
     * the archive_yn flip and the file deletion, the orphaned files are
     * removed on the next full sweep. {@code deleteForNews} is idempotent
     * (no folder → 0), so re-sweeping already-clean ids is a cheap
     * directory check.</p>
     *
     * @param fullSweep false = incremental (daily / after import),
     *                  true = full sweep (weekly)
     * @return the number of archived news items
     */
    public int runArchiveCleanup(boolean fullSweep) {
        try {
            Instant cutoff = Instant.now().minus(Duration.ofDays(properties.getArchiveDays()));
            // The @Modifying archiveOlderThan query requires an active
            // transaction. Wrap the SELECT + UPDATE in a short transaction;
            // image cleanup (file I/O) runs outside to keep the tx short.
            final int[] archivedHolder = {0};
            sourceTransaction.execute(status -> {
                archivedHolder[0] = newsRepository.archiveOlderThan(cutoff);
                return null;
            });
            List<Long> sweepIds = fullSweep
                    ? newsRepository.findArchivedIds()
                    : newsRepository.findArchivedIdsSince(Instant.now().minus(Duration.ofDays(1)));
            int imagesDeleted = 0;
            for (Long newsId : sweepIds) {
                imagesDeleted += imageStore.deleteForNews(newsId);
            }
            if (imagesDeleted > 0) {
                log.info("AI-NEWS-IMPORT archive cleanup ({} sweep) removed {} image file(s) "
                        + "for {} news item(s)", fullSweep ? "full" : "incremental",
                        imagesDeleted, sweepIds.size());
            }
            return archivedHolder[0];
        } catch (Exception e) {
            log.error("AI-NEWS-IMPORT archive cleanup failed (will retry next run): {}",
                    e.getMessage());
            return 0;
        }
    }

    private JobRunResult doRun(String triggerType) {
        Instant startedAt = Instant.now();
        Instant deadline = startedAt.plus(MAX_RUN);
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
                if (Instant.now().isAfter(deadline)) {
                    throw new DeadlineExceeded(deadlineMessage());
                }
                try {
                    SourceResult result = processSource(source, deadline);
                    fetched += result.fetched();
                    inserted += result.inserted();
                    skipped += result.skipped();
                } catch (DeadlineExceeded e) {
                    fatal = true;
                    errorMessage = e.getMessage();
                    log.error("AI-NEWS-IMPORT {}", e.getMessage());
                    break;
                } catch (Exception e) {
                    // BR-NEWS-006: one failing source must not stop the others.
                    failedSources++;
                    errorMessage = "source " + source.getMailbox() + " failed: " + e.getMessage();
                    log.error("AI-NEWS-IMPORT source {} failed: {}", source.getMailbox(), e.getMessage());
                }
            }

            // Step 6 — archive cleanup (BR-NEWS-010), best-effort: a failure
            // here does NOT change the job status (import already succeeded).
            // Incremental sweep (last 24h) — the weekly full sweep
            // (AiNewsArchiveCleanupScheduler) keeps the self-healing property.
            if (!fatal) {
                archived = runArchiveCleanup(false);
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

    private static String deadlineMessage() {
        return "exceeded max run time (" + MAX_RUN.toMinutes() + " minutes) — stopping";
    }

    /**
     * Fetches + imports one source and updates last_success_run_at on success
     * (BR-NEWS-007).
     *
     * <p>Each email gets a SHORT transaction (dedup + insert only). The image
     * download/rewrite (blocking network I/O, up to 20 images × 10s) runs
     * OUTSIDE the transaction, followed by a second short transaction that
     * updates the body — a slow third-party image host must not hold a pooled
     * DB connection open for minutes.</p>
     */
    private SourceResult processSource(AiNewsSource source, Instant deadline) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(Duration.ofDays(properties.getImportWindowDays()));
        Instant since = source.getLastSuccessRunAt() != null
                ? source.getLastSuccessRunAt()
                : windowStart;
        String password = secretProvider.resolve(source.getSecretRef());

        List<EmailRecord> emails;
        try {
            emails = emailReader.read(
                    source.getMailbox(),
                    password,
                    "INBOX",
                    since,
                    properties.getFetchLimit(),
                    deadline);
        } catch (EmailReaderException e) {
            // The reader enforces the wall-clock deadline between pages and
            // detail fetches (a slow portal: 20 pages + 50 details × 60s
            // would otherwise run ~70 min, past the 10-minute cap). Surface
            // that as a run-level deadline breach (FAILED) rather than a
            // per-source failure (PARTIAL) — the whole run is over.
            if (e.getMessage() != null && e.getMessage().contains("deadline exceeded")) {
                throw new DeadlineExceeded(deadlineMessage());
            }
            throw e;
        }

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
            final String body = capBody(htmlSanitizer.sanitize(email.bodyHtml() != null
                    ? email.bodyHtml()
                    : email.bodyText()));
            final String rawMessageId = email.messageId();
            final boolean fallback = rawMessageId == null || rawMessageId.isBlank();
            // Truncate ONCE up front so the dedup check and the insert operate
            // on the SAME key: saveNews stores at most MESSAGE_ID_MAX chars
            // (VARCHAR(320) UNIQUE). Checking the untruncated id would never
            // match a previously stored truncated value (unique violation →
            // misclassified "db integrity" skip every run), and a different
            // Message-ID sharing the first 320 chars would be skipped
            // forever (silent data loss).
            final String baseMessageId = truncateMessageId(fallback
                    ? sha256(safe(email.from()) + "|" + email.date() + "|" + safe(email.subject()))
                    : rawMessageId.trim());
            final Instant createdAt = email.date();
            final String sender = email.from();
            final String sourceName = source.getDisplayName();
            final Map<String, byte[]> cidImages = email.cidImages();

            // Short transaction: dedup + insert only (no network I/O inside).
            // The body is inserted as-is; the image pipeline (blocking
            // network I/O, up to 20 images × 10s) runs OUTSIDE the
            // transaction and the rewritten body is persisted in a second
            // short transaction — a slow third-party image host must not
            // hold a pooled DB connection open. A single pathological email
            // (e.g. a message_id unique-constraint race) must not abort the
            // rest of the source: catch, count as skipped, continue.
            AiNews insertedNews = null;
            try {
                insertedNews = sourceTransaction.execute(status -> {
                    if (newsRepository.existsByMessageId(baseMessageId)) {
                        // Real Message-ID already imported → dedup skip
                        // (BR-NEWS-002). Fallback hash collision → try
                        // -1, -2, ... (§4.3).
                        if (!fallback) {
                            skipped.incrementAndGet();
                            return null;
                        }
                        int suffix = 1;
                        String candidate = baseMessageId + "-" + suffix;
                        while (newsRepository.existsByMessageId(candidate)) {
                            candidate = baseMessageId + "-" + (++suffix);
                        }
                        return saveNews(candidate, title, body, sender, sourceName, createdAt);
                    }
                    return saveNews(baseMessageId, title, body, sender, sourceName, createdAt);
                });
            } catch (org.springframework.dao.DataAccessException e) {
                // e.g. message_id unique-constraint violation (truncation
                // collision or check-then-insert race) — skip this email.
                skipped.incrementAndGet();
                log.warn("AI-NEWS-IMPORT skipping email (db integrity): {}", e.getMessage());
                continue;
            }
            if (insertedNews == null) {
                continue; // dedup-skipped
            }
            inserted.incrementAndGet();
            Long newsId = insertedNews.getNewsId();

            // Image pipeline OUTSIDE the transaction. Best-effort: an image
            // failure never breaks the import (the frontend onerror fallback
            // hides unresolvable images).
            if (body != null && !body.isBlank()) {
                if (Instant.now().isAfter(deadline)) {
                    throw new DeadlineExceeded(deadlineMessage());
                }
                String processed = body;
                try {
                    String rewritten = imageStore.extractAndStore(processed, newsId,
                            cidImages != null ? cidImages : Map.of());
                    if (rewritten != null && !rewritten.equals(processed)) {
                        processed = rewritten;
                    }
                } catch (Exception e) {
                    log.warn("AI-NEWS-IMPORT image extraction failed for news {}: {}",
                            newsId, e.getMessage());
                }
                processed = imageStore.inlineOversizedCidImages(processed, cidImages);
                processed = imageStore.stripDeadRemoteImages(processed);
                if (!processed.equals(body)) {
                    // Second short transaction: persist the rewritten body
                    // (re-save the same entity — the JPA provider detects the
                    // managed entity and issues an UPDATE).
                    try {
                        final String finalBody = processed;
                        final AiNews newsToUpdate = insertedNews;
                        sourceTransaction.executeWithoutResult(status -> {
                            newsToUpdate.setBody(finalBody);
                            newsRepository.save(newsToUpdate);
                        });
                    } catch (Exception e) {
                        log.warn("AI-NEWS-IMPORT body update failed for news {}: {}",
                                newsId, e.getMessage());
                    }
                }
            }
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

    /** Inserts one news row (called inside the short per-email transaction). */
    private AiNews saveNews(String messageId, String title, String body, String sender,
                            String sourceName, Instant createdAt) {
        AiNews news = AiNews.builder()
                .messageId(truncateMessageId(messageId))
                .title(title)
                .body(body)
                .sender(sender)
                .source(sourceName)
                .createdAt(createdAt)
                .build();
        AiNews saved = newsRepository.save(news);
        return saved != null ? saved : news;
    }

    /**
     * BR-NEWS-012: FAILED/PARTIAL 3 consecutive runs → alert Ops (email +
     * log alert). The email is best-effort: a send failure is logged but must
     * never break the job. When {@code ai-news.alert-email} is unset the
     * alert is log-only.
     */
    private void alertIfConsecutiveFailures() {
        List<AiNewsBatchLog> recent = logRepository.findTop3NonSkippedByOrderByRunIdDesc();
        if (recent.size() < CONSECUTIVE_FAIL_ALERT) {
            return;
        }
        boolean allFailing = recent.stream().allMatch(l ->
                AiNewsBatchLog.STATUS_FAILED.equals(l.getStatus())
                        || AiNewsBatchLog.STATUS_PARTIAL.equals(l.getStatus()));
        if (!allFailing) {
            return;
        }
        String lastError = recent.get(0).getErrorMessage();
        log.error("ALERT: AI-NEWS-IMPORT FAILED/PARTIAL {} consecutive runs — notify Ops "
                + "(BR-NEWS-012). Last error: {}", recent.size(), lastError);

        String recipient = properties.getAlertEmail();
        if (recipient == null || recipient.isBlank()) {
            log.info("AI-NEWS-IMPORT alert: ai-news.alert-email not set — log-only alert");
            return;
        }
        try {
            emailService.sendNotificationEmail(recipient,
                    "[AI-NEWS-IMPORT] FAILED/PARTIAL " + recent.size() + " consecutive runs (BR-NEWS-012)",
                    "The AI-NEWS-IMPORT batch job has FAILED or PARTIAL for " + recent.size()
                            + " consecutive runs.\n"
                            + "Last error: " + lastError + "\n"
                            + "Check the batch logs (GET /api/v1/admin/ai-news/logs) and the "
                            + "Knox portal / secret configuration (AI_NEWS_KNOX_BASE_URL, "
                            + "AI_NEWS_SECRET_*).");
        } catch (Exception e) {
            log.error("AI-NEWS-IMPORT alert email to {} failed: {}", recipient, e.getMessage());
        }
    }

    /** Title = subject, leading bracketed prefixes ([ANN], [GUIDE]...) stripped, truncated to 200. */
    public static String cleanTitle(String subject) {
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

    /** Truncates an oversized body (attacker-controlled portal HTML) to {@link #BODY_MAX}. */
    public static String capBody(String body) {
        if (body == null || body.length() <= BODY_MAX) {
            return body;
        }
        return body.substring(0, BODY_MAX) + "<p>[content truncated — email body exceeded size limit]</p>";
    }

    /**
     * Truncates a message id to the stored column width (VARCHAR(320) UNIQUE).
     * Callers MUST use this value for BOTH the dedup check and the insert —
     * checking the untruncated id would never match a stored truncated value
     * (unique violation every re-fetch) and a different id sharing the first
     * 320 chars would be skipped forever (silent data loss).
     */
    public static String truncateMessageId(String messageId) {
        return messageId.length() > MESSAGE_ID_MAX
                ? messageId.substring(0, MESSAGE_ID_MAX)
                : messageId;
    }

    public static String sha256(String input) {
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
