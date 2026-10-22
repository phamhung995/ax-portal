package com.skillmanagement.ainews.batch;

import com.skillmanagement.ainews.batch.AiNewsBatchProperties;
import com.skillmanagement.ainews.batch.AiNewsBatchService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
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
    private PlatformTransactionManager transactionManager;

    private AiNewsBatchProperties properties;
    private AiNewsBatchService service;

    @BeforeEach
    void setUp() {
        properties = new AiNewsBatchProperties();
        properties.setFetchLimit(50);
        properties.setImportWindowDays(2);
        properties.setArchiveDays(90);
        // Tests use the placeholder host; make the fallback a no-op so the
        // resolved host equals the configured one (mocks match on it).
        properties.setDefaultImapHost("imap.internal");

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
        lenient().when(logRepository.findTop3ByOrderByRunIdDesc()).thenReturn(List.of());

        service = new AiNewsBatchService(sourceRepository, newsRepository, logRepository,
                emailReader, new HtmlSanitizer(), secretProvider, properties, transactionManager);
    }

    private AiNewsSource source(String mailbox) {
        return AiNewsSource.builder()
                .sourceId(1L)
                .mailbox(mailbox)
                .imapHost("imap.internal")
                .imapPort((short) 993)
                .secretRef(mailbox)
                .displayName(mailbox + " Display")
                .status(AiNewsSource.STATUS_ACTIVE)
                .build();
    }

    private EmailRecord email(String messageId, String subject, Instant date) {
        return new EmailRecord(messageId, subject, "sender@" + messageId + ".internal",
                null, date, "plain body", "<p>html body</p>", null);
    }

    private void mockSingleSource(AiNewsSource source, List<EmailRecord> emails) {
        when(sourceRepository.findByStatus(AiNewsSource.STATUS_ACTIVE)).thenReturn(List.of(source));
        when(secretProvider.resolve(source.getSecretRef())).thenReturn("secret");
        when(emailReader.read(eq(source.getImapHost()), eq(993), eq(source.getMailbox()),
                anyString(), eq("INBOX"), any(Instant.class), anyInt())).thenReturn(emails);
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
                "auto", null, "auto-replied");
        EmailRecord emptySubject = new EmailRecord("<e@internal>", "  ",
                "x@internal", null, Instant.now().minus(1, ChronoUnit.HOURS), "body", null, null);
        mockSingleSource(source, List.of(autoReply, emptySubject,
                email("<ok@internal>", "Real", Instant.now().minus(1, ChronoUnit.HOURS))));

        AiNewsBatchService.JobRunResult result = service.run(AiNewsBatchLog.TRIGGER_CRON);

        assertThat(result.totalInserted()).isEqualTo(1);
        assertThat(result.totalSkipped()).isEqualTo(2);
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
        when(emailReader.read(anyString(), anyInt(), eq("partx"), anyString(),
                eq("INBOX"), any(Instant.class), anyInt()))
                .thenThrow(new EmailReaderException("auth failed"));
        when(emailReader.read(anyString(), anyInt(), eq("gdc"), anyString(),
                eq("INBOX"), any(Instant.class), anyInt()))
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
        verify(emailReader).read(anyString(), anyInt(), anyString(), anyString(),
                eq("INBOX"), since.capture(), anyInt());
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
        when(emailReader.read(anyString(), anyInt(), anyString(), anyString(),
                eq("INBOX"), any(Instant.class), anyInt())).thenAnswer(inv -> {
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
}
