package com.furelise.skillmanagement.ainews.service;

import com.furelise.skillmanagement.ainews.batch.AiNewsBatchService;
import com.furelise.skillmanagement.ainews.dto.MhtImportResult;
import com.furelise.skillmanagement.ainews.email.EmailRecord;
import com.furelise.skillmanagement.ainews.email.HtmlSanitizer;
import com.furelise.skillmanagement.ainews.email.MhtEmailParser;
import com.furelise.skillmanagement.ainews.image.AiNewsImageStore;
import com.furelise.skillmanagement.ainews.model.AiNews;
import com.furelise.skillmanagement.ainews.repository.AiNewsRepository;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Manual .mht import for AI News (AI_News_Screen_SRS.md v1.14 FR-21).
 *
 * <p>Admin uploads 1..{@value #MAX_FILES} .mht files; each file is parsed
 * ({@link MhtEmailParser}), sanitized ({@link HtmlSanitizer}) and inserted
 * into {@code ai_news} through the SAME pipeline as the Knox portal batch job
 * (dedup by message_id, CID image storage via {@link AiNewsImageStore}).
 *
 * <p>Isolation rules:
 * <ul>
 *   <li>One file failing does NOT stop the others — each file runs in its
 *       own REQUIRES_NEW transaction (same pattern as per-source
 *       transactions in {@link AiNewsBatchService}).</li>
 *   <li>Does NOT touch {@code ai_news_source.last_success_run_at} and does
 *       NOT write {@code ai_news_batch_log} — manual import is not a job run.</li>
 *   <li>CID images above the image store's 2MB cap are inlined as base64
 *       data-URIs instead of being dropped (the store leaves them as
 *       {@code cid:} srcs, which the browser cannot resolve).</li>
 * </ul>
 */
@Service
@Slf4j
public class AiNewsMhtImportService {

    static final int MAX_FILES = 20;
    /** Per-file / total caps — mirror the frontend contract (20MB/file, 40MB total). */
    static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 40L * 1024 * 1024;
    static final String SOURCE_NAME = "Manual Upload";
    /**
     * Sender label for .mht files without a From header (desktop mail client
     * exports — Outlook/MimeOLE strip the envelope). The real sender is not
     * recoverable from such files, so the row is labeled with the source type
     * instead of being left null.
     */
    static final String DESKTOP_SENDER = "KnoxDesktop";

    private final AiNewsRepository newsRepository;
    private final HtmlSanitizer htmlSanitizer;
    private final AiNewsImageStore imageStore;
    private final MhtEmailParser mhtEmailParser;
    private final TransactionTemplate fileTransaction;

    public AiNewsMhtImportService(AiNewsRepository newsRepository,
                                  HtmlSanitizer htmlSanitizer,
                                  AiNewsImageStore imageStore,
                                  MhtEmailParser mhtEmailParser,
                                  PlatformTransactionManager transactionManager) {
        this.newsRepository = newsRepository;
        this.htmlSanitizer = htmlSanitizer;
        this.imageStore = imageStore;
        this.mhtEmailParser = mhtEmailParser;
        this.fileTransaction = new TransactionTemplate(transactionManager);
        this.fileTransaction.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Imports a batch of .mht files.
     *
     * @param files uploaded files (1..20, each .mht)
     * @return one result per file (INSERTED / SKIPPED_DUPLICATE / FAILED)
     * @throws BusinessException MHT_INVALID_FILE when the batch is empty or a
     *                           file does not have a .mht extension
     */
    public List<MhtImportResult> importFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.MHT_INVALID_FILE,
                    "No .mht file uploaded");
        }
        if (files.size() > MAX_FILES) {
            throw new BusinessException(BusinessErrorCode.MHT_INVALID_FILE,
                    "Maximum " + MAX_FILES + " .mht files per import");
        }
        long totalBytes = 0;
        for (MultipartFile file : files) {
            if (!isMhtFile(file)) {
                throw new BusinessException(BusinessErrorCode.MHT_INVALID_FILE,
                        "Only .mht files can be imported: " + file.getOriginalFilename());
            }
            // Server-side mirror of the frontend limits (20MB/file, 40MB total)
            // — a direct API call must not bypass the documented contract.
            totalBytes += file.getSize();
            if (file.getSize() > MAX_FILE_BYTES) {
                throw new BusinessException(BusinessErrorCode.MHT_INVALID_FILE,
                        "File too large (max 20MB): " + file.getOriginalFilename());
            }
            if (totalBytes > MAX_TOTAL_BYTES) {
                throw new BusinessException(BusinessErrorCode.MHT_INVALID_FILE,
                        "Total size exceeds 40MB — remove some files");
            }
        }

        return java.util.stream.IntStream.range(0, files.size())
                .mapToObj(i -> importOne(i, files.get(i)))
                .toList();
    }

    private MhtImportResult importOne(int index, MultipartFile file) {
        String fileName = file.getOriginalFilename() == null ? "file-" + index : file.getOriginalFilename();
        try {
            EmailRecord record;
            try (var in = file.getInputStream()) {
                record = mhtEmailParser.parse(in);
            }
            return insertNews(index, fileName, record);
        } catch (Exception e) {
            log.warn("AI-NEWS-MHT-IMPORT file {} failed: {}", fileName, e.getMessage());
            // Fixed message — the raw exception text (parser internals, file
            // paths) must not be reflected into the API response.
            return new MhtImportResult(index, fileName, MhtImportResult.STATUS_FAILED,
                    null, "Import failed");
        }
    }

    /**
     * Inserts one parsed email (mirrors {@code AiNewsBatchService.insertNews}).
     *
     * <p>The dedup + insert run in a short REQUIRES_NEW transaction; the
     * image pipeline (CID storage, base64 inlining) runs OUTSIDE it — a
     * failure there must not roll back the already-imported row, and the
     * body rewrite is persisted in its own short transaction.</p>
     */
    private MhtImportResult insertNews(int index, String fileName, EmailRecord record) {
        // Desktop mail clients (Outlook / Microsoft MimeOLE) save .mht WITHOUT
        // envelope headers (no Subject/From/Date/Message-ID) — only the MIME
        // body. Such a file is named after the subject line (prefixed with the
        // send date), so recover the title from the file name when the Subject
        // header is absent. Web/portal exports carry a real Subject header and
        // use it directly.
        String subject = record.subject();
        if (subject == null || subject.isBlank()) {
            subject = titleFromFileName(fileName);
        }
        String title = AiNewsBatchService.cleanTitle(subject);
        if (title.isBlank()) {
            return new MhtImportResult(index, fileName, MhtImportResult.STATUS_FAILED,
                    null, "Email has no subject");
        }

        // Dedup key for header-less files (no Message-ID): hash the title + body
        // content. This is stable across re-imports of the same email — unlike
        // the sent date, which is unknown for header-less files and would be
        // now(), breaking dedup on a second import.
        String bodyContent = record.bodyHtml() != null ? record.bodyHtml() : record.bodyText();
        String rawMessageId = record.messageId();
        boolean fallback = rawMessageId == null || rawMessageId.isBlank();
        // Truncate ONCE up front so the dedup check and the insert operate
        // on the SAME key (VARCHAR(320) UNIQUE) — same contract as the
        // batch pipeline (see AiNewsBatchService.processSource).
        String messageId = AiNewsBatchService.truncateMessageId(fallback
                ? "mht-" + AiNewsBatchService.sha256(safe(title) + "|" + safe(bodyContent))
                : rawMessageId.trim());

        String body = AiNewsBatchService.capBody(htmlSanitizer.sanitize(record.bodyHtml() != null
                ? record.bodyHtml()
                : record.bodyText()));
        Instant createdAt = record.date() != null ? record.date() : Instant.now();

        // Short transaction: dedup + insert only.
        AiNews insertedNews = fileTransaction.execute(status -> {
            if (newsRepository.existsByMessageId(messageId)) {
                return null;
            }
            AiNews news = AiNews.builder()
                    .messageId(messageId)
                    .title(title)
                    .body(body)
                    .sender(record.from() != null && !record.from().isBlank()
                            ? record.from()
                            : DESKTOP_SENDER)
                    .source(SOURCE_NAME)
                    .createdAt(createdAt)
                    .build();
            AiNews saved = newsRepository.save(news);
            return saved != null ? saved : news;
        });
        if (insertedNews == null) {
            return new MhtImportResult(index, fileName, MhtImportResult.STATUS_SKIPPED_DUPLICATE,
                    null, null);
        }
        Long newsId = insertedNews.getNewsId();

        // Image pipeline OUTSIDE the transaction. Best-effort: an image
        // failure never breaks the import (the frontend onerror fallback
        // hides unresolvable images).
        if (body != null && !body.isBlank()) {
            String processed = body;
            try {
                String rewritten = imageStore.extractAndStore(processed, newsId,
                        record.cidImages() != null ? record.cidImages() : Map.of());
                if (rewritten != null && !rewritten.equals(processed)) {
                    processed = rewritten;
                }
            } catch (Exception e) {
                log.warn("AI-NEWS-MHT-IMPORT image extraction failed for news {}: {}",
                        newsId, e.getMessage());
            }
            // CID images above the store's size cap were left as cid: srcs —
            // inline them as data-URIs so the body renders completely.
            processed = imageStore.inlineOversizedCidImages(processed, record.cidImages());
            // Remote images the store could not fetch (403 temp URLs) can never
            // render — drop them instead of leaving broken images.
            processed = imageStore.stripDeadRemoteImages(processed);
            if (!processed.equals(body)) {
                // Second short transaction: persist the rewritten body
                // (re-save the same entity — the JPA provider detects the
                // managed entity and issues an UPDATE).
                try {
                    final String finalBody = processed;
                    final AiNews newsToUpdate = insertedNews;
                    fileTransaction.executeWithoutResult(status -> {
                        newsToUpdate.setBody(finalBody);
                        newsRepository.save(newsToUpdate);
                    });
                } catch (Exception e) {
                    log.warn("AI-NEWS-MHT-IMPORT body update failed for news {}: {}",
                            newsId, e.getMessage());
                }
            }
        }
        return new MhtImportResult(index, fileName, MhtImportResult.STATUS_INSERTED,
                newsId, null);
    }

    private static boolean isMhtFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".mht");
    }

    /**
     * Recovers a title from a .mht file name for header-less (desktop) exports.
     *
     * <p>Desktop mail clients (Outlook) save the file as
     * {@code <YYYYMMDD>_<subject>.mht} — the send date prefix followed by the
     * subject line (with real spaces). Stripping the leading date prefix yields
     * the subject, which matches the Subject header of the same email exported
     * from the web. The date prefix is required: file names without it are not
     * part of the desktop naming convention, so an empty string is returned
     * (the import then fails with "Email has no subject" rather than guessing
     * a title). Files that already carry a Subject header never reach this
     * method.</p>
     */
    static String titleFromFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String stem = fileName;
        int dot = stem.lastIndexOf('.');
        if (dot >= 0) {
            stem = stem.substring(0, dot);
        }
        // Only desktop exports named <YYYYMMDD>_<subject>.mht are recoverable —
        // the send-date prefix is the client's naming convention, and its
        // presence guarantees the remainder matches the web export's Subject.
        // File names without the prefix are not part of the convention, so we
        // do not guess a title from them (a blank Subject still fails import).
        if (!stem.matches("\\d{8}_.+")) {
            return "";
        }
        return stem.replaceFirst("^\\d{8}_", "").trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
