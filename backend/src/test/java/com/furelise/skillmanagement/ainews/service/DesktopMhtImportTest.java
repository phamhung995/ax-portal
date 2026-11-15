package com.furelise.skillmanagement.ainews.service;

import com.furelise.skillmanagement.ainews.dto.MhtImportResult;
import com.furelise.skillmanagement.ainews.email.HtmlSanitizer;
import com.furelise.skillmanagement.ainews.image.AiNewsImageStore;
import com.furelise.skillmanagement.ainews.model.AiNews;
import com.furelise.skillmanagement.ainews.repository.AiNewsRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for header-less .mht files saved by desktop mail clients
 * (Outlook / Microsoft MimeOLE). Such files carry NO envelope headers
 * (Subject/From/Date/Message-ID) — only the MIME body — so the import used to
 * fail with "Email has no subject". The fix recovers the title from the file
 * name (the desktop client names the file {@code <YYYYMMDD>_<subject>.mht})
 * and uses a content-based dedup key.
 *
 * <p>The fixture {@code desktop_nosubject.mht} is a real Outlook export of the
 * same email whose web export (with full headers) imports fine.</p>
 */
@ExtendWith(MockitoExtension.class)
class DesktopMhtImportTest {

    @Mock
    private AiNewsRepository newsRepository;
    @Mock
    private AiNewsImageStore imageStore;
    @Mock
    private PlatformTransactionManager transactionManager;

    private AiNewsMhtImportService service;
    private Path imageStoreDir;

    @BeforeEach
    void setUp() throws Exception {
        imageStoreDir = Files.createTempDirectory("desktop-mht-images");
        lenient().when(transactionManager.getTransaction(any())).thenAnswer(inv ->
                mock(TransactionStatus.class));
        lenient().when(newsRepository.save(any(AiNews.class))).thenAnswer(inv -> {
            AiNews news = inv.getArgument(0);
            if (news.getNewsId() == null) {
                news.setNewsId(2000L);
            }
            return news;
        });
        AiNewsImageStore realStore =
                new AiNewsImageStore(imageStoreDir.toString(), "/uploads/ai-news-images");
        lenient().when(imageStore.extractAndStore(any(), any(), any())).thenAnswer(inv ->
                inv.getArgument(0));
        lenient().when(imageStore.inlineOversizedCidImages(any(), any())).thenAnswer(inv ->
                realStore.inlineOversizedCidImages(inv.getArgument(0), inv.getArgument(1)));
        lenient().when(imageStore.stripDeadRemoteImages(any())).thenAnswer(inv ->
                inv.getArgument(0));
        // REAL parser — the point is to parse the actual header-less file.
        service = new AiNewsMhtImportService(newsRepository, new HtmlSanitizer(),
                imageStore, new com.furelise.skillmanagement.ainews.email.MhtEmailParser(),
                transactionManager);
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, "message/rfc822", content);
    }

    private byte[] desktopBytes() {
        try {
            return Files.readAllBytes(Path.of("src/test/resources/mht/desktop_nosubject.mht"));
        } catch (IOException e) {
            throw new IllegalStateException("desktop_nosubject.mht missing", e);
        }
    }

    @Test
    @DisplayName("header-less desktop .mht imports: title recovered from file name")
    void importsHeaderLessDesktopFile() throws Exception {
        when(newsRepository.existsByMessageId(any())).thenReturn(false);

        List<MhtImportResult> results = service.importFiles(List.of(
                file("20261023_(FigSA MCP) FigSA for the whole team, even with view seats.mht",
                        desktopBytes())));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(MhtImportResult.STATUS_INSERTED);
        assertThat(results.get(0).newsId()).isEqualTo(2000L);

        ArgumentCaptor<AiNews> captor = ArgumentCaptor.forClass(AiNews.class);
        verify(newsRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        AiNews saved = captor.getValue();
        // Date prefix stripped; cleanTitle only strips [..] prefixes (not (..)),
        // so the title matches the web export's subject exactly.
        assertThat(saved.getTitle())
                .isEqualTo("(FigSA MCP) FigSA for the whole team, even with view seats");
        // No From header → sender labeled with the source type (the real
        // sender is not recoverable from desktop exports).
        assertThat(saved.getSender()).isEqualTo("KnoxDesktop");
        // Content-based dedup key (no Message-ID in the file).
        assertThat(saved.getMessageId()).startsWith("mht-");
        // Body parsed from the MIME part.
        assertThat(saved.getBody()).isNotBlank();
    }

    @Test
    @DisplayName("re-importing the same header-less file → SKIPPED_DUPLICATE (stable dedup key)")
    void headerLessDuplicateSkipped() throws Exception {
        when(newsRepository.existsByMessageId(any())).thenReturn(true);

        List<MhtImportResult> results = service.importFiles(List.of(
                file("20261023_(FigSA MCP) FigSA for the whole team, even with view seats.mht",
                        desktopBytes())));

        assertThat(results.get(0).status()).isEqualTo(MhtImportResult.STATUS_SKIPPED_DUPLICATE);
        verify(newsRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("titleFromFileName strips the leading YYYYMMDD_ date prefix")
    void titleFromFileNameStripsDatePrefix() {
        assertThat(AiNewsMhtImportService.titleFromFileName(
                "20261023_(FigSA MCP) FigSA for the whole team, even with view seats.mht"))
                .isEqualTo("(FigSA MCP) FigSA for the whole team, even with view seats");
        // No date prefix → NOT part of the desktop naming convention → no
        // title is guessed (a blank Subject still fails the import).
        assertThat(AiNewsMhtImportService.titleFromFileName("Plain Subject.mht"))
                .isEmpty();
        // null → empty.
        assertThat(AiNewsMhtImportService.titleFromFileName(null)).isEmpty();
    }
}
