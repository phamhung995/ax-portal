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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiNewsBatchServiceTest {

    @Mock
    private AiNewsSourceRepository sourceRepository;
    @Mock
    private AiNewsRepository newsRepository;
    @Mock
    private AiNewsBatchLogRepository logRepository;
    @Mock
    private EmailReader emailReader;
    @Mock
    private MailboxSecretProvider secretProvider;
    @Mock
    private AiNewsImageStore imageStore;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private EmailService emailService;

    private AiNewsBatchProperties properties;
    private AiNewsBatchService service;

    @BeforeEach
    void setUp() {
        properties = new AiNewsBatchProperties();
        properties.setFetchLimit(50);
        properties.setImportWindowDays(2);
        properties.setArchiveDays(90);

        // TransactionTemplate: execute the callback directly (no real transaction).
        // getTransaction() is called with the template as argument; the callback
        // receives the returned status (must be non-null or the callback is skipped).
        lenient().when(transactionManager.getTransaction(any())).thenAnswer(inv ->
                org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class));

        // Default: log repo returns the running row itself.
        lenient().when(logRepository.save(any(AiNewsBatchLog.class))).thenAnswer(inv -> {
            AiNewsBatchLog log = inv.getArgument(0);
            if (log.getRunId() == null) {
                log.setRunId(1L);
            }
            return log;
        });
        lenient().when(logRepository.findTop3NonSkippedByOrderByRunIdDesc()).thenReturn(List.of());

        // Image store: no-op by default (returns the body unchanged).
        lenient().when(imageStore.extractAndStore(any(), any(), any())).thenAnswer(inv ->
                inv.getArgument(0));
        lenient().when(imageStore.inlineOversizedCidImages(any(), any())).thenAnswer(inv ->
                inv.getArgument(0));
        lenient().when(imageStore.stripDeadRemoteImages(any())).thenAnswer(inv ->
                inv.getArgument(0));
        // Archive cleanup: incremental sweep (last 24h) — no ids by default
        // (image sweep is a no-op).
        lenient().when(newsRepository.findArchivedIdsSince(any(Instant.class))).thenReturn(List.of());
        lenient().when(newsRepository.findArchivedIds()).thenReturn(List.of());

        service = new AiNewsBatchService(sourceRepository, newsRepository, logRepository,
                emailReader, new HtmlSanitizer(), secretProvider, imageStore, properties,
                emailService, transactionManager);
    }

    private AiNewsSource source(String mailbox) {
        return AiNewsSource.builder()
                .sourceId(1L)
                .mailbox(mailbox)
                .secretRef(mailbox)
                .displayName(mailbox + " Display")
                .status(AiNewsSource.STATUS_ACTIVE)
                .build();
    }

    private EmailRecord email(String messageId, String subject, Instant date) {
        return new EmailRecord(messageId, subject, "sender@" + messageId + ".internal",
                null, date, "plain body", "<p>html body</p>", null, Map.of());
    }

    private void mockSingleSource(AiNewsSource source, List<EmailRecord> emails) {
        when(sourceRepository.findByStatus(AiNewsSource.STATUS_ACTIVE)).thenReturn(List.of(source));
        when(secretProvider.resolve(source.getSecretRef())).thenReturn("secret");
        when(emailReader.read(eq(source.getMailbox()), anyString(),
                eq("INBOX"), any(Instant.class), anyInt(), any(Instant.class))).thenReturn(emails);
    }

    @Test
    @DisplayName("imports new emails, sanitizes body, sets source display name (AC-NEWS-01)")
    void importsNewEmails() {
        AiNewsSource source = source("gdc");
        mockSingleSource(source, List.of(
                email("<a@internal>", "[ANN] GDC AI Workshop", Instant.now().minus(1, ChronoUnit.HOURS))));

        AiNewsBatchService.JobRunResult result = service.run(AiNewsBatchLog.TRIGGER_CRON);

        ArgumentCaptor<AiNews> captor = ArgumentCaptor.forClass(AiNews.class);
        verify(newsRepository).save(captor.capture());
        AiNews saved = captor.getValue();
        assertThat(saved.getMessageId()).isEqualTo("<a@internal>");
        assertThat(saved.getTitle()).isEqualTo("GDC AI Workshop");
        assertThat(saved.getSender()).isEqualTo("sender@<a@internal>.internal");
        assertThat(saved.getSource()).isEqualTo("gdc Display");
        assertThat(saved.getBody()).isEqualTo("<p>html body</p>");

        assertThat(result.status()).isEqualTo(AiNewsBatchLog.STATUS_SUCCESS);
        assertThat(result.totalInserted()).isEqualTo(1);
        assertThat(result.totalSkipped()).isZero();
    }

    @Test
    @DisplayName("idempotent: existing message_id is skipped (AC-NEWS-02)")
    void idempotentDedup() {
        AiNewsSource source = source("gdc");
        mockSingleSource(source, List.of(
                email("<a@internal>", "Title A", Instant.now().minus(1, ChronoUnit.HOURS))));
        when(newsRepository.existsByMessageId("<a@internal>")).thenReturn(true);

        AiNewsBatchService.JobRunResult result = service.run(AiNewsBatchLog.TRIGGER_CRON);

        verify(newsRepository, never()).save(any(AiNews.class));
        assertThat(result.status()).isEqualTo(AiNewsBatchLog.STATUS_SUCCESS);
        assertThat(result.totalInserted()).isZero();
        assertThat(result.totalSkipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("email older than the 2-day window is skipped (AC-NEWS-03)")
    void skipsOldEmails() {
        AiNewsSource source = source("gdc");
        mockSingleSource(source, List.of(
                email("<old@internal>", "Old", Instant.now().minus(3, ChronoUnit.DAYS)),
                email("<new@internal>", "New", Instant.now().minus(1, ChronoUnit.DAYS))));

        AiNewsBatchService.JobRunResult result = service.run(AiNewsBatchLog.TRIGGER_CRON);

        assertThat(result.totalInserted()).isEqualTo(1);
        assertThat(result.totalSkipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("auto-reply and empty-subject emails are skipped (AC-NEWS-04)")
    void skipsAutoReplies() {
        AiNewsSource source = source("gdc");
        EmailRecord autoReply = new EmailRecord("<r@internal>", "Re: something",
                "bot@internal", null, Instant.now().minus(1, ChronoUnit.HOURS),
                "auto", null, "auto-replied", Map.of());
        EmailRecord emptySubject = new EmailRecord("<e@internal>", "  ",
                "x@internal", null, Instant.now().minus(1, ChronoUnit.HOURS), "body", null, null, Map.of());
        mockSingleSource(source, List.of(autoReply, emptySubject,
                email("<ok@internal>", "Real", Instant.now().minus(1, ChronoUnit.HOURS))));

        AiNewsBatchService.JobRunResult result = service.run(AiNewsBatchLog.TRIGGER_CRON);

        assertThat(result.totalInserted()).isEqualTo(1);
        assertThat(result.totalSkipped()).isEqualTo(2);
    }

    @Test
    @DisplayName("bounce (auto-generated) emails are skipped (§3.3 rule 3)")
    void skipsBounce() {
        AiNewsSource source = source("gdc");
        EmailRecord bounce = new EmailRecord("<bounce@internal>", "Undeliverable: report",
                "mailer-daemon@internal", null, Instant.now().minus(1, ChronoUnit.HOURS),
                "bounce body", null, "auto-generated", Map.of());
        mockSingleSource(source, List.of(bounce,
                email("<ok@internal>", "Real", Instant.now().minus(1, ChronoUnit.HOURS))));

        AiNewsBatchService.JobRunResult result = service.run(AiNewsBatchLog.TRIGGER_CRON);

        assertThat(result.totalInserted()).isEqualTo(1);
        assertThat(result.totalSkipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("one source failing does not block others; status PARTIAL, marker unchanged (AC-NEWS-05)")
    void oneSourceFails() {
        AiNewsSource failing = source("partx");
        AiNewsSource ok = source("gdc");
        ok.setSourceId(2L);
        when(sourceRepository.findByStatus(AiNewsSource.STATUS_ACTIVE))
                .thenReturn(List.of(failing, ok));
        when(secretProvider.resolve(anyString())).thenReturn("secret");
        when(emailReader.read(eq("partx"), anyString(),
                eq("INBOX"), any(Instant.class), anyInt(), any(Instant.class)))
                .thenThrow(new EmailReaderException("auth failed"));
        when(emailReader.read(eq("gdc"), anyString(),
                eq("INBOX"), any(Instant.class), anyInt(), any(Instant.class)))
                .thenReturn(List.of(email("<g@internal>", "GDC", Instant.now().minus(1, ChronoUnit.HOURS))));

        AiNewsBatchService.JobRunResult result = service.run(AiNewsBatchLog.TRIGGER_CRON);

        assertThat(result.status()).isEqualTo(AiNewsBatchLog.STATUS_PARTIAL);
        assertThat(result.totalFailed()).isEqualTo(1);
        assertThat(result.totalInserted()).isEqualTo(1);

        // Failing source: last_success_run_at must NOT be updated.
        ArgumentCaptor<AiNewsSource> captor = ArgumentCaptor.forClass(AiNewsSource.class);
        verify(sourceRepository).save(captor.capture());
        AiNewsSource saved = captor.getValue();
        assertThat(saved.getMailbox()).isEqualTo("gdc");
        assertThat(failing.getLastSuccessRunAt()).isNull();
    }

    @Test
    @DisplayName("fetch == limit → marker = MAX(created_at) - 1s (BR-NEWS-009)")
    void limitReachedMarker() {
        AiNewsSource source = source("gdc");
        Instant newest = Instant.now().minus(1, ChronoUnit.MINUTES);
        List<EmailRecord> emails = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            emails.add(email("<m" + i + "@internal>", "M" + i,
                    newest.minus(49 - i, ChronoUnit.MINUTES)));
        }
        mockSingleSource(source, emails);

        AiNewsBatchService.JobRunResult result = service.run(AiNewsBatchLog.TRIGGER_CRON);

        assertThat(result.totalInserted()).isEqualTo(50);
        ArgumentCaptor<AiNewsSource> captor = ArgumentCaptor.forClass(AiNewsSource.class);
        verify(sourceRepository).save(captor.capture());
        assertThat(captor.getValue().getLastSuccessRunAt())
                .isEqualTo(newest.minus(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("fetch < limit → marker = now()")
    void underLimitMarker() {
        AiNewsSource source = source("gdc");
        mockSingleSource(source, List.of(
                email("<a@internal>", "A", Instant.now().minus(1, ChronoUnit.HOURS))));
        Instant before = Instant.now();

        service.run(AiNewsBatchLog.TRIGGER_CRON);

        ArgumentCaptor<AiNewsSource> captor = ArgumentCaptor.forClass(AiNewsSource.class);
        verify(sourceRepository).save(captor.capture());
        assertThat(captor.getValue().getLastSuccessRunAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("new source (last_success_run_at NULL) → since = now() - 2d")
    void newSourceSince() {
        AiNewsSource source = source("gdc");
        mockSingleSource(source, List.of());
        Instant before = Instant.now();

        service.run(AiNewsBatchLog.TRIGGER_CRON);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(emailReader).read(anyString(), anyString(),
                eq("INBOX"), since.capture(), anyInt(), any(Instant.class));
        assertThat(since.getValue()).isBetween(
                before.minus(2, ChronoUnit.DAYS).minusSeconds(5),
                before.minus(2, ChronoUnit.DAYS).plusSeconds(5));
    }

    @Test
    @DisplayName("concurrent run throws NEWS_JOB_RUNNING (AC-NEWS-07)")
    void concurrentRunSkipped() throws Exception {
        AiNewsSource source = source("gdc");
        CountDownLatch block = new CountDownLatch(1);
        when(sourceRepository.findByStatus(AiNewsSource.STATUS_ACTIVE)).thenReturn(List.of(source));
        when(secretProvider.resolve(source.getSecretRef())).thenReturn("secret");
        when(emailReader.read(anyString(), anyString(),
                eq("INBOX"), any(Instant.class), anyInt(), any(Instant.class))).thenAnswer(inv -> {
            block.await(10, TimeUnit.SECONDS);
            return List.of();
        });

        Thread first = new Thread(() -> service.run(AiNewsBatchLog.TRIGGER_CRON));
        first.start();
        // Wait until the first run holds the lock.
        Thread.sleep(300);

        assertThatThrownBy(() -> service.run(AiNewsBatchLog.TRIGGER_MANUAL))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(BusinessErrorCode.NEWS_JOB_RUNNING);

        // NFR-NEWS-06: the rejected concurrent trigger leaves a SKIPPED log row.
        ArgumentCaptor<AiNewsBatchLog> logCaptor = ArgumentCaptor.forClass(AiNewsBatchLog.class);
        verify(logRepository, atLeast(2)).save(logCaptor.capture());
        assertThat(logCaptor.getAllValues())
                .anyMatch(l -> AiNewsBatchLog.STATUS_SKIPPED.equals(l.getStatus()));

        block.countDown();
        first.join(5000);
    }

    @Test
    @DisplayName("archive cleanup runs after import (AC-NEWS-09)")
    void archiveCleanup() {
        AiNewsSource source = source("gdc");
        mockSingleSource(source, List.of());
        when(newsRepository.archiveOlderThan(any(Instant.class))).thenReturn(7);

        AiNewsBatchService.JobRunResult result = service.run(AiNewsBatchLog.TRIGGER_CRON);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(newsRepository).archiveOlderThan(cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(Instant.now().minus(89, ChronoUnit.DAYS));
        assertThat(result.totalArchived()).isEqualTo(7);
    }

    @Test
    @DisplayName("archive cleanup failure does not change job status")
    void archiveFailureBestEffort() {
        AiNewsSource source = source("gdc");
        mockSingleSource(source, List.of(
                email("<a@internal>", "A", Instant.now().minus(1, ChronoUnit.HOURS))));
        when(newsRepository.archiveOlderThan(any(Instant.class)))
                .thenThrow(new RuntimeException("db down"));

        AiNewsBatchService.JobRunResult result = service.run(AiNewsBatchLog.TRIGGER_CRON);

        assertThat(result.status()).isEqualTo(AiNewsBatchLog.STATUS_SUCCESS);
        assertThat(result.totalArchived()).isZero();
    }

    @Test
    @DisplayName("import-run cleanup is an INCREMENTAL sweep (last 24h), not the full archived set")
    void importRunUsesIncrementalSweep() {
        AiNewsSource source = source("gdc");
        mockSingleSource(source, List.of());
        when(newsRepository.archiveOlderThan(any(Instant.class))).thenReturn(0);
        when(newsRepository.findArchivedIdsSince(any(Instant.class))).thenReturn(List.of(10L));

        service.run(AiNewsBatchLog.TRIGGER_CRON);

        verify(imageStore).deleteForNews(10L);
        verify(newsRepository, never()).findArchivedIds();
    }

    @Test
    @DisplayName("full sweep (weekly) deletes image folders of the ENTIRE archived set (self-healing)")
    void fullSweepCoversAllArchivedIds() {
        when(newsRepository.archiveOlderThan(any(Instant.class))).thenReturn(0);
        when(newsRepository.findArchivedIds()).thenReturn(List.of(1L, 2L, 3L));

        service.runArchiveCleanup(true);

        verify(imageStore).deleteForNews(1L);
        verify(imageStore).deleteForNews(2L);
        verify(imageStore).deleteForNews(3L);
        verify(newsRepository, never()).findArchivedIdsSince(any(Instant.class));
    }

    @Test
    @DisplayName("run log is written with final status and totals")
    void writesRunLog() {
        AiNewsSource source = source("gdc");
        mockSingleSource(source, List.of(
                email("<a@internal>", "A", Instant.now().minus(1, ChronoUnit.HOURS))));

        service.run(AiNewsBatchLog.TRIGGER_MANUAL);

        ArgumentCaptor<AiNewsBatchLog> captor = ArgumentCaptor.forClass(AiNewsBatchLog.class);
        verify(logRepository, atLeast(2)).save(captor.capture());
        AiNewsBatchLog finalLog = captor.getValue();
        assertThat(finalLog.getStatus()).isEqualTo(AiNewsBatchLog.STATUS_SUCCESS);
        assertThat(finalLog.getTriggerType()).isEqualTo(AiNewsBatchLog.TRIGGER_MANUAL);
        assertThat(finalLog.getTotalInserted()).isEqualTo(1);
        assertThat(finalLog.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("empty Message-ID → SHA256 fallback; collision → -1 suffix (§4.3)")
    void messageIdFallback() {
        AiNewsSource source = source("gdc");
        mockSingleSource(source, List.of(
                email(null, "Same", Instant.now().minus(1, ChronoUnit.HOURS))));
        // The fallback hash collides → -1 suffix used.
        when(newsRepository.existsByMessageId(anyString())).thenReturn(true, false);

        service.run(AiNewsBatchLog.TRIGGER_CRON);

        ArgumentCaptor<AiNews> captor = ArgumentCaptor.forClass(AiNews.class);
        verify(newsRepository).save(captor.capture());
        String id = captor.getValue().getMessageId();
        assertThat(id).hasSize(66); // 64 hex chars + "-1"
        assertThat(id).endsWith("-1");
    }

    @Test
    @DisplayName("title: bracketed prefixes stripped, truncated to 200")
    void cleanTitle() {
        assertThat(AiNewsBatchService.cleanTitle("[ANN] [GUIDE] Hello"))
                .isEqualTo("Hello");
        assertThat(AiNewsBatchService.cleanTitle("x".repeat(250))).hasSize(200);
        assertThat(AiNewsBatchService.cleanTitle("[only-bracket")).isEqualTo("[only-bracket");
    }

    @Test
    @DisplayName("message id: truncated to the stored column width (320) — dedup check + insert use the same key")
    void messageIdTruncatedToColumnWidth() {
        assertThat(AiNewsBatchService.truncateMessageId("a".repeat(400))).hasSize(320);
        assertThat(AiNewsBatchService.truncateMessageId("short")).isEqualTo("short");
    }
}
