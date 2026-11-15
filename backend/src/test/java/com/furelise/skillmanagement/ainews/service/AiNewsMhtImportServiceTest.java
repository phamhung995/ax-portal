package com.furelise.skillmanagement.ainews.service;

import com.furelise.skillmanagement.ainews.dto.MhtImportResult;
import com.furelise.skillmanagement.ainews.email.EmailRecord;
import com.furelise.skillmanagement.ainews.email.HtmlSanitizer;
import com.furelise.skillmanagement.ainews.email.MhtEmailParser;
import com.furelise.skillmanagement.ainews.email.MhtParseException;
import com.furelise.skillmanagement.ainews.image.AiNewsImageStore;
import com.furelise.skillmanagement.ainews.model.AiNews;
import com.furelise.skillmanagement.ainews.repository.AiNewsRepository;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiNewsMhtImportServiceTest {

    @Mock
    private AiNewsRepository newsRepository;
    @Mock
    private AiNewsImageStore imageStore;
    @Mock
    private MhtEmailParser mhtEmailParser;
    @Mock
    private PlatformTransactionManager transactionManager;

    private AiNewsMhtImportService service;
    private Path imageStoreDir;

    @BeforeEach
    void setUp() throws Exception {
        imageStoreDir = Files.createTempDirectory("mht-test-images");
        lenient().when(transactionManager.getTransaction(any())).thenAnswer(inv ->
                mock(TransactionStatus.class));
        lenient().when(newsRepository.save(any(AiNews.class))).thenAnswer(inv -> {
            AiNews news = inv.getArgument(0);
            if (news.getNewsId() == null) {
                news.setNewsId(1000L);
            }
            return news;
        });
        // Image store: extractAndStore is a no-op (returns the body unchanged);
        // inlineOversizedCidImages delegates to a REAL store so the end-to-end
        // sample test still inlines CID images as data-URIs.
        AiNewsImageStore realStore =
                new AiNewsImageStore(imageStoreDir.toString(), "/uploads/ai-news-images");
        lenient().when(imageStore.extractAndStore(any(), any(), any())).thenAnswer(inv ->
                inv.getArgument(0));
        lenient().when(imageStore.inlineOversizedCidImages(any(), any())).thenAnswer(inv ->
                realStore.inlineOversizedCidImages(inv.getArgument(0), inv.getArgument(1)));
        lenient().when(imageStore.stripDeadRemoteImages(any())).thenAnswer(inv ->
                inv.getArgument(0));
        service = new AiNewsMhtImportService(newsRepository, new HtmlSanitizer(),
                imageStore, mhtEmailParser, transactionManager);
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name,
                "message/rfc822", content);
    }

    private byte[] sampleBytes() {
        try {
            return Files.readAllBytes(Path.of("src/test/resources/mht/sample.mht"));
        } catch (IOException e) {
            throw new IllegalStateException("sample.mht missing", e);
        }
    }

    private EmailRecord record(String messageId, String subject, Instant date) {
        return new EmailRecord(messageId, subject, "Sender <sender@internal>",
                null, date, null, "<p>body</p>", null, Map.of());
    }

    @Test
    @DisplayName("imports the real sample: metadata from file, INSERTED")
    void importsRealSample() throws Exception {
        // Use the REAL parser for the end-to-end sample (mocked elsewhere).
        AiNewsMhtImportService realParserService = new AiNewsMhtImportService(
                newsRepository, new HtmlSanitizer(), imageStore, new MhtEmailParser(),
                transactionManager);
        when(newsRepository.existsByMessageId(any())).thenReturn(false);

        List<MhtImportResult> results = realParserService.importFiles(
                List.of(file("20261013_[Did_You_Know]_AI_Helps.mht", sampleBytes())));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(MhtImportResult.STATUS_INSERTED);
        assertThat(results.get(0).newsId()).isEqualTo(1000L);

        ArgumentCaptor<AiNews> captor = ArgumentCaptor.forClass(AiNews.class);
        verify(newsRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        AiNews saved = captor.getValue();
        // cleanTitle strips the leading [Did You Know] bracketed prefix (batch rule).
        assertThat(saved.getTitle())
                .isEqualTo("AI Helps In 5 Five-Steps of SR Handling Journey 👀");
        assertThat(saved.getSender()).isEqualTo("Nguyen Thi Hoang Nhung <nth.nhung3@samsung.com>");
        assertThat(saved.getSource()).isEqualTo("Manual Upload");
        assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-10-16T14:06:54Z"));
        // 2.3MB CID image inlined as data-URI (image store no-op in test).
        assertThat(saved.getBody()).contains("data:image/png;base64,");
        // Inline style attributes survive sanitization.
        assertThat(saved.getBody()).contains("style=");
    }

    @Test
    @DisplayName("duplicate message_id → SKIPPED_DUPLICATE, no save")
    void skipsDuplicate() {
        when(mhtEmailParser.parse(any())).thenReturn(
                record("<dup@internal>", "Title", Instant.now()));
        when(newsRepository.existsByMessageId("<dup@internal>")).thenReturn(true);

        List<MhtImportResult> results = service.importFiles(
                List.of(file("a.mht", new byte[]{1, 2, 3})));

        assertThat(results.get(0).status()).isEqualTo(MhtImportResult.STATUS_SKIPPED_DUPLICATE);
        verify(newsRepository, never()).save(any());
    }

    @Test
    @DisplayName("one failing file does not stop the others")
    void oneFailureDoesNotStopOthers() {
        when(mhtEmailParser.parse(any()))
                .thenReturn(record("<a@internal>", "First", Instant.now()))
                .thenThrow(new MhtParseException("corrupt"));
        when(newsRepository.existsByMessageId(any())).thenReturn(false);

        List<MhtImportResult> results = service.importFiles(List.of(
                file("a.mht", new byte[]{1}),
                file("b.mht", new byte[]{2})));

        assertThat(results.get(0).status()).isEqualTo(MhtImportResult.STATUS_INSERTED);
        assertThat(results.get(1).status()).isEqualTo(MhtImportResult.STATUS_FAILED);
        // Fixed message — the raw exception text (parser internals) must not
        // be reflected into the API response (security fix).
        assertThat(results.get(1).error()).isEqualTo("Import failed");
        assertThat(results.get(1).error()).doesNotContain("corrupt");
    }

    @Test
    @DisplayName("blank subject → FAILED")
    void blankSubjectFails() {
        when(mhtEmailParser.parse(any())).thenReturn(
                record("<n@internal>", "   ", Instant.now()));

        List<MhtImportResult> results = service.importFiles(
                List.of(file("a.mht", new byte[]{1})));

        assertThat(results.get(0).status()).isEqualTo(MhtImportResult.STATUS_FAILED);
        assertThat(results.get(0).error()).contains("subject");
        verify(newsRepository, never()).save(any());
    }

    @Test
    @DisplayName("missing Message-ID → mht- prefixed sha256 fallback")
    void messageIdFallback() {
        when(mhtEmailParser.parse(any())).thenReturn(
                record(null, "Title", Instant.parse("2026-10-16T14:06:54Z")));
        when(newsRepository.existsByMessageId(any())).thenReturn(false);

        service.importFiles(List.of(file("a.mht", new byte[]{1})));

        ArgumentCaptor<AiNews> captor = ArgumentCaptor.forClass(AiNews.class);
        verify(newsRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getMessageId()).startsWith("mht-");
    }

    @Test
    @DisplayName("non-.mht file → MHT_INVALID_FILE")
    void rejectsNonMht() {
        assertThatThrownBy(() -> service.importFiles(
                List.of(file("a.txt", new byte[]{1}))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(BusinessErrorCode.MHT_INVALID_FILE));
    }

    @Test
    @DisplayName("empty batch → MHT_INVALID_FILE")
    void rejectsEmpty() {
        assertThatThrownBy(() -> service.importFiles(List.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("file over 20MB → MHT_INVALID_FILE (server-side mirror of the frontend limit)")
    void rejectsOversizedFile() {
        byte[] big = new byte[(int) AiNewsMhtImportService.MAX_FILE_BYTES + 1];
        assertThatThrownBy(() -> service.importFiles(List.of(file("a.mht", big))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(BusinessErrorCode.MHT_INVALID_FILE));
    }

    @Test
    @DisplayName("total over 40MB → MHT_INVALID_FILE (server-side mirror of the frontend limit)")
    void rejectsOversizedTotal() {
        // 3 files × ~14MB = ~42MB total (each under the 20MB per-file cap).
        int size = (int) (14 * 1024 * 1024);
        byte[] chunk = new byte[size];
        assertThatThrownBy(() -> service.importFiles(List.of(
                file("a.mht", chunk), file("b.mht", chunk), file("c.mht", chunk))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(BusinessErrorCode.MHT_INVALID_FILE));
    }
}
