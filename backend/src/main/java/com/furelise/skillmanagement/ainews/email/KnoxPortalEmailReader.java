package com.furelise.skillmanagement.ainews.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.furelise.skillmanagement.ainews.batch.AiNewsBatchProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Knox C&C Suite portal (web mail) implementation of the email-reading skill
 * (AI_News_Batch.md §3.1).
 *
 * <p>Reads a mailbox through the portal web API instead of IMAP (Knox does not
 * expose IMAP): log in to the portal sign-in API with the mailbox
 * user/password (secret store), then read the inbox via the mail REST API
 * (list + per-mail detail). Each {@link #read} call uses its own per-run
 * {@link Session} (credentials + cookie jar), so the reader is safe to call
 * from multiple threads and no mailbox password lingers in instance state
 * after the run (BR-NEWS-011).</p>
 *
 * <p>Contract:
 * <ul>
 *   <li>Read-only — only list/read calls, never send/delete/flag (NFR-NEWS-04).</li>
 *   <li>60s timeout per request; any failure (timeout/auth/network) is wrapped
 *       in {@link EmailReaderException} so the batch job can log the source
 *       error and continue (BR-NEWS-006).</li>
 *   <li>Results sorted by date ASCENDING (oldest first) — required by
 *       BR-NEWS-009.</li>
 *   <li>Per mail, the reader downloads the .mht (MIME message/rfc822) via
 *       the portal's mhtml/download endpoint and parses it — the record then
 *       carries the real Message-ID (dedup BR-NEWS-002 matches manual
 *       imports), the inline images (cidImages → the AI News image store)
 *       and the Auto-Submitted header (§3.3 rule 3). When the download or
 *       parse fails, the reader falls back to the plain detail (read) call:
 *       {@code messageId = "knox-" + mailId}, body from the detail HTML,
 *       no images (SRS §8 — the mail is still imported).</li>
 * </ul>
 *
 * <p>When the fetch hits {@code limit}, the OLDEST {@code limit} matching
 * emails are selected — independent of the portal's page order. The portal's
 * page order is not part of the probed contract (webmail UIs typically list
 * newest first), so it is detected from the first page's dates: ascending
 * pages are walked forward (skipping all-old pages), descending pages stop at
 * the first item older than {@code since} (older items are on later pages and
 * cannot match), and an undetectable order falls back to fetching all pages
 * and taking the oldest matching items. Selecting the oldest items is what
 * makes the BR-NEWS-009 marker (MAX fetched date − 1s on a limit hit) safe:
 * the overflow is always NEWER than the marker and gets fetched next run
 * (dedup absorbs the re-fetch). Selecting the newest 50 instead would push
 * the marker past the older, unfetched emails — silent data loss.</p>
 *
 * <p>Endpoint paths and field names come from {@link AiNewsBatchProperties.Knox}
 * (defaults probed from the portal SPA bundles) so a contract change is a
 * config update, not a code change. A detail-fetch failure for one mail does
 * not fail the source — the mail is imported with {@code bodyHtml = null}
 * (SRS §8: broken/empty body → import with body NULL).</p>
 */
@Component
@Slf4j
public class KnoxPortalEmailReader implements EmailReader {

    /** Per-request timeout (AI_News_Batch.md §3.1: 60s per mailbox). */
    static final Duration TIMEOUT = Duration.ofSeconds(60);
    /** Pagination safety cap — 20 pages × page size bounds the worst case. */
    static final int MAX_PAGES = 20;

    private final ObjectMapper objectMapper;
    private final AiNewsBatchProperties.Knox knox;
    private final MhtEmailParser mhtParser;

    public KnoxPortalEmailReader(ObjectMapper objectMapper, AiNewsBatchProperties properties,
                                 MhtEmailParser mhtParser) {
        this.objectMapper = objectMapper;
        this.knox = properties.getKnox();
        this.mhtParser = mhtParser;
        String baseUrl = properties.getKnox().getBaseUrl();
        if (baseUrl != null && baseUrl.startsWith("http://")) {
            // The mail API carries the portal session cookie — over plaintext
            // HTTP the cookie is exposed on the network path.
            log.warn("AI-NEWS-IMPORT knox base-url is plaintext HTTP ({}): the portal "
                    + "session cookie travels unencrypted. Prefer https:// if the portal "
                    + "supports it (AI_NEWS_KNOX_BASE_URL).", baseUrl);
        }
    }

    @Override
    public List<EmailRecord> read(String username, String password,
                                  String folder, Instant since, int limit,
                                  Instant deadline) {
        if (limit <= 0) {
            return List.of();
        }
        Session session = null;
        try {
            session = new Session(username, password);
            // Proactive login before the first API call — the portal session
            // cookie is only issued by the sign-in endpoint, so every read()
            // starts with a fresh login (cheap: one POST, no data transfer).
            session.doLogin();
            List<JsonNode> matching = fetchMatchingItems(session, since, limit, deadline);
            // Oldest first — required by BR-NEWS-009 (overflow must be newer than the marker).
            List<EmailRecord> result = new ArrayList<>(Math.min(matching.size(), limit));
            List<DatedItem> dated = new ArrayList<>(matching.size());
            for (JsonNode item : matching) {
                dated.add(new DatedItem(parseDate(item.path(knox.getDateField()).asText(""), zone()), item));
            }
            dated.sort(Comparator.comparing(DatedItem::date,
                    Comparator.nullsFirst(Comparator.naturalOrder())));
            for (DatedItem item : dated) {
                if (result.size() >= limit) {
                    break;
                }
                // Deadline between detail fetches: 50 sequential detail
                // calls × 60s timeout would otherwise blow past the job's
                // 10-minute cap (NFR-NEWS-01).
                if (deadline != null && Instant.now().isAfter(deadline)) {
                    throw new EmailReaderException("Knox portal read deadline exceeded "
                            + "for " + username);
                }
                result.add(toRecord(session, item.item()));
            }
            return result;
        } catch (EmailReaderException e) {
            throw e;
        } catch (InterruptedException e) {
            // Restore the interrupt flag — the batch job's timeout/interrupt
            // path relies on it to actually stop the run.
            Thread.currentThread().interrupt();
            throw new EmailReaderException("Knox portal read interrupted for " + username, e);
        } catch (Exception e) {
            // Never log the password (BR-NEWS-011 / NFR-NEWS-02).
            throw new EmailReaderException("Knox portal read failed for " + username
                    + ": " + e.getMessage(), e);
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    /**
     * Pages through the mail list and collects the OLDEST {@code limit}
     * emails newer than {@code since}, regardless of the portal's page order
     * (see the class javadoc for why the oldest must be the ones selected).
     */
    private List<JsonNode> fetchMatchingItems(Session session, Instant since, int limit,
                                              Instant deadline) throws Exception {
        List<JsonNode> matching = new ArrayList<>();
        PageOrder order = PageOrder.UNKNOWN;
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            // Deadline between pages: 20 list pages × 60s timeout would
            // otherwise blow past the job's 10-minute cap (NFR-NEWS-01).
            if (deadline != null && Instant.now().isAfter(deadline)) {
                throw new EmailReaderException("Knox portal read deadline exceeded "
                        + "while paging the mail list");
            }
            String url = knox.getBaseUrl() + knox.getMailListPath()
                    + "?form=lnb&pageNo=" + pageNo + "&count=" + knox.getPageSize();
            String body = objectMapper.writeValueAsString(Map.of(
                    "form", "lnb", "pageNo", pageNo, "count", knox.getPageSize()));
            JsonNode page = session.postJson(url, body);
            JsonNode items = page.path(knox.getListItemsField());
            if (!items.isArray() || items.isEmpty()) {
                break;
            }
            if (pageNo == 1) {
                order = detectOrder(items);
            }
            boolean shortPage = items.size() < knox.getPageSize();
            if (order == PageOrder.ASC) {
                // Oldest first: pages entirely older than `since` can be
                // skipped; stop at the limit or the last page.
                boolean anyMatch = false;
                for (JsonNode item : items) {
                    Instant date = parseDate(item.path(knox.getDateField()).asText(""), zone());
                    if (date != null && date.isAfter(since)) {
                        matching.add(item);
                        anyMatch = true;
                    }
                }
                if (!anyMatch) {
                    continue;
                }
                if (matching.size() >= limit || shortPage) {
                    break;
                }
            } else if (order == PageOrder.DESC) {
                // Newest first: the first item older than `since` means every
                // item on this and all later pages is older — stop.
                for (JsonNode item : items) {
                    Instant date = parseDate(item.path(knox.getDateField()).asText(""), zone());
                    if (date != null && !date.isAfter(since)) {
                        return matching;
                    }
                    matching.add(item);
                }
                if (matching.size() >= limit || shortPage) {
                    break;
                }
            } else {
                // Undetectable order (all dates equal/unparseable): fetch all
                // pages and take the oldest matching items afterwards.
                for (JsonNode item : items) {
                    Instant date = parseDate(item.path(knox.getDateField()).asText(""), zone());
                    if (date != null && date.isAfter(since)) {
                        matching.add(item);
                    }
                }
                if (shortPage) {
                    break;
                }
            }
        }
        if (order == PageOrder.UNKNOWN && matching.size() > limit) {
            log.warn("AI-NEWS-IMPORT could not detect the portal page order — fetched "
                    + "all pages and kept the oldest {} of {} matching emails", limit,
                    matching.size());
            matching.sort(Comparator.comparing(item ->
                    parseDate(item.path(knox.getDateField()).asText(""), zone()),
                    Comparator.nullsFirst(Comparator.naturalOrder())));
            matching.subList(limit, matching.size()).clear();
        }
        return matching;
    }

    /** Detects the portal's page order from the first page's dates. */
    private PageOrder detectOrder(JsonNode items) {
        Instant first = null;
        Instant last = null;
        for (JsonNode item : items) {
            Instant date = parseDate(item.path(knox.getDateField()).asText(""), zone());
            if (date == null) {
                continue;
            }
            if (first == null) {
                first = date;
            }
            last = date;
        }
        if (first == null || last == null) {
            return PageOrder.UNKNOWN;
        }
        if (last.isAfter(first)) {
            return PageOrder.ASC;
        }
        if (last.isBefore(first)) {
            return PageOrder.DESC;
        }
        return PageOrder.UNKNOWN;
    }

    private enum PageOrder {
        ASC, DESC, UNKNOWN
    }

    /**
     * Maps one list item to an {@link EmailRecord}.
     *
     * <p>Primary path: download the mail as .mht (MIME message/rfc822) via
     * the portal's mhtml/download endpoint and parse it — the record then
     * carries the real Message-ID (dedup matches manual imports), the inline
     * images and the Auto-Submitted header. Fallback path (download or parse
     * failure): the plain detail (read) call — body from the detail HTML,
     * {@code messageId = "knox-" + mailId}, no images (SRS §8 — the mail is
     * still imported). A detail failure yields {@code bodyHtml = null}.</p>
     */
    private EmailRecord toRecord(Session session, JsonNode item) throws Exception {
        String mailId = item.path(knox.getMailIdField()).asText("");
        if (mailId.isBlank()) {
            throw new EmailReaderException("Knox portal list item is missing the mail id");
        }
        String subject = item.path(knox.getSubjectField()).asText(null);
        String from = formatSender(
                item.path(knox.getSenderNameField()).asText(""),
                item.path(knox.getSenderField()).asText(""));
        Instant date = parseDate(item.path(knox.getDateField()).asText(""), zone());
        // folderID + mailSeq are shared by both the mhtml download and the
        // detail call — URL-encoded (a raw '&' or '#' would corrupt the URL).
        String folderId = URLEncoder.encode(
                item.path(knox.getFolderIdField()).asText(""), StandardCharsets.UTF_8);
        String mailSeq = URLEncoder.encode(
                item.path(knox.getMailSeqField()).asText(""), StandardCharsets.UTF_8);

        EmailRecord mhtRecord = tryMhtmlDownload(session, mailId, folderId, mailSeq,
                subject, from, date);
        if (mhtRecord != null) {
            return mhtRecord;
        }

        // Fallback: the plain detail call (body only, no images).
        String bodyHtml = null;
        try {
            String readUrl = knox.getBaseUrl() + knox.getMailReadPath()
                    + "?" + knox.getFolderIdField() + "=" + folderId
                    + "&" + knox.getMailSeqField() + "=" + mailSeq;
            String body = objectMapper.writeValueAsString(
                    Map.of(knox.getMailIdField(), mailId));
            JsonNode detail = session.postJson(readUrl, body);
            String html = detail.path(knox.getBodyHtmlField()).asText(null);
            if (html != null && !html.isBlank()) {
                bodyHtml = html;
            }
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            // Best-effort: the title/sender/date are still imported.
            log.warn("AI-NEWS-IMPORT Knox portal detail failed for mail {} — body left null: {}",
                    mailId, e.getMessage());
        }
        return new EmailRecord("knox-" + mailId, subject, from, null,
                date != null ? date : Instant.now(), null, bodyHtml, null, Map.of());
    }

    /**
     * Downloads the mail as .mht and parses it. Returns the parsed record
     * (real Message-ID, inline images, Auto-Submitted header, Date header),
     * or {@code null} when the download or parse fails — the caller falls
     * back to the plain detail call.
     *
     * <p>The list item's subject/sender/date are the canonical display values
     * (same as the fallback path); the MIME headers are used for the
     * Message-ID, the To address, the auto-reply header and the images. The
     * MIME Date header is preferred for the record date (more accurate than
     * the list item's date) and falls back to the list item's date.</p>
     */
    private EmailRecord tryMhtmlDownload(Session session, String mailId,
                                         String folderId, String mailSeq,
                                         String subject, String from, Instant listDate) {
        try {
            String url = knox.getBaseUrl() + knox.getMhtmlDownloadPath()
                    + "?" + knox.getFolderIdField() + "=" + folderId
                    + "&" + knox.getMailSeqField() + "=" + mailSeq;
            HttpResponse<byte[]> response = session.postRaw(url, "{}");
            if (response.statusCode() != 200) {
                log.warn("AI-NEWS-IMPORT Knox portal mhtml download failed for mail {} (HTTP {})",
                        mailId, response.statusCode());
                return null;
            }
            EmailRecord mht = mhtParser.parse(new java.io.ByteArrayInputStream(response.body()));
            return new EmailRecord(mht.messageId(),
                    subject != null && !subject.isBlank() ? subject : mht.subject(),
                    from != null && !from.isBlank() ? from : mht.from(),
                    mht.to(),
                    mht.date() != null ? mht.date() : listDate,
                    mht.bodyText(), mht.bodyHtml(), mht.autoSubmitted(), mht.cidImages());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailReaderException("Knox portal mhtml download interrupted for mail "
                    + mailId, e);
        } catch (Exception e) {
            log.warn("AI-NEWS-IMPORT Knox portal mhtml download/parse failed for mail {} — "
                    + "falling back to the detail call: {}", mailId, e.getMessage());
            return null;
        }
    }

    private static String formatSender(String name, String email) {
        if (name.isBlank() && email.isBlank()) {
            return null;
        }
        if (name.isBlank()) {
            return email;
        }
        if (email.isBlank()) {
            return name;
        }
        return name + " <" + email + ">";
    }

    /**
     * One read() call's portal session: the mailbox credentials plus an HTTP
     * client with its own cookie jar. Per-run (not shared instance state) so
     * concurrent reads cannot race on credentials/cookies and the password is
     * released as soon as the run finishes.
     */
    private final class Session {
        private String username;
        private String password;
        private final HttpClient httpClient;

        Session(String username, String password) {
            this.username = username;
            this.password = password;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }

        void close() {
            // Release the credentials from heap as soon as the run ends.
            this.username = null;
            this.password = null;
            // Release the client's connection pool + executor threads
            // (JDK 21 HttpClient is AutoCloseable) — otherwise each run
            // leaks a thread pool until GC.
            try {
                this.httpClient.close();
            } catch (Exception e) {
                log.debug("AI-NEWS-IMPORT closing portal HTTP client: {}", e.getMessage());
            }
        }

        /** POSTs JSON and returns the parsed response. One re-login + retry on
         *  401/403 (session expired mid-run). */
        JsonNode postJson(String url, String jsonBody) throws Exception {
            for (int attempt = 0; attempt < 2; attempt++) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    if (attempt == 0) {
                        log.warn("AI-NEWS-IMPORT Knox portal session expired (HTTP {}) — re-login",
                                response.statusCode());
                        doLogin();
                        continue;
                    }
                    throw new EmailReaderException("Knox portal auth failed (HTTP "
                            + response.statusCode() + ")");
                }
                if (response.statusCode() != 200) {
                    throw new EmailReaderException("Knox portal HTTP " + response.statusCode()
                            + ": " + truncate(response.body(), 200));
                }
                return objectMapper.readTree(response.body());
            }
            throw new IllegalStateException("unreachable");
        }

        /** POSTs a JSON body and returns the raw response bytes (the .mht
         *  download endpoint answers with {@code message/rfc822}, not JSON).
         *  One re-login + retry on 401/403, like {@link #postJson}. */
        HttpResponse<byte[]> postRaw(String url, String jsonBody) throws Exception {
            for (int attempt = 0; attempt < 2; attempt++) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                HttpResponse<byte[]> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    if (attempt == 0) {
                        log.warn("AI-NEWS-IMPORT Knox portal session expired (HTTP {}) — re-login",
                                response.statusCode());
                        doLogin();
                        continue;
                    }
                    throw new EmailReaderException("Knox portal auth failed (HTTP "
                            + response.statusCode() + ")");
                }
                return response;
            }
            throw new IllegalStateException("unreachable");
        }

        /** Logs in to the portal sign-in API. The response's {@code loginStatus}
         *  must be SUCCESS — anything else (NOT_EXIST_ID, wrong password, ...)
         *  fails the source with a clear message.
         *
         * <p>The payload mirrors the real loginapp SPA form (verified against a
         * browser capture): the portal rejects the bare id/password pair with
         * {@code NOT_CHECKED_USERINFO_AGREE} unless the form's default flags
         * (serviceLevelInit, icode, mode, ...) are present, and the field
         * names are {@code loginID}/{@code userPassword} (capital ID).</p> */
        private void doLogin() throws Exception {
            String json = objectMapper.writeValueAsString(Map.of(
                    knox.getLoginUserIdField(), username,
                    knox.getLoginPasswordField(), password,
                    "language", "en_US.EUC-KR",
                    "isSaveId", false,
                    "serviceLevelInit", "N",
                    "isUsePasskey", false,
                    "isBypassSameLoginLimit", false,
                    "icode", "N",
                    "knoxTraySetterYn", "Y",
                    "mode", "normal"));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(knox.getLoginBaseUrl() + knox.getLoginPath()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new EmailReaderException("Knox portal login HTTP " + response.statusCode());
            }
            JsonNode node = objectMapper.readTree(response.body());
            String status = node.path(knox.getLoginStatusField()).asText("");
            if (!"SUCCESS".equalsIgnoreCase(status)) {
                throw new EmailReaderException("Knox portal login failed: " + status);
            }
        }
    }

    /**
     * Lenient date parser — the portal's date format is probed, not fixed:
     * epoch seconds (10 digits), epoch millis (13 digits), ISO-8601,
     * {@code yyyy-MM-dd HH:mm:ss}, {@code yyyyMMddHHmmss}, RFC-1123, or
     * {@code yyyyMMdd}. Returns null when unparseable (the caller treats a
     * null date as "not newer than since" for filtering, and as now() for the
     * record date).
     *
     * <p>Zone-less formats are interpreted in the portal's zone
     * ({@link #zone()}, default {@code Asia/Seoul}) — NOT the JVM default.
     * The prod container runs UTC; the portal is a Korea service, so
     * {@code systemDefault()} would shift every zone-less date by 7 hours
     * (wrong created_at, and a BR-NEWS-009 marker in the shifted instant
     * space that can drop boundary emails permanently).</p>
     */
    static Instant parseDate(String raw) {
        return parseDate(raw, ZoneId.systemDefault());
    }

    static Instant parseDate(String raw, ZoneId zone) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            if (value.matches("\\d{10}")) {
                return Instant.ofEpochSecond(Long.parseLong(value));
            }
            if (value.matches("\\d{13}")) {
                return Instant.ofEpochMilli(Long.parseLong(value));
            }
            if (value.matches("\\d{14}")) {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                        .atZone(zone).toInstant();
            }
            if (value.contains("T")) {
                try {
                    return ZonedDateTime.parse(value).toInstant();
                } catch (DateTimeParseException ignored) {
                    // no zone — interpret in the portal's zone
                    return LocalDateTime.parse(value).atZone(zone).toInstant();
                }
            }
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .atZone(zone).toInstant();
            } catch (DateTimeParseException ignored) {
                // try next format
            }
            try {
                return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            } catch (DateTimeParseException ignored) {
                // try next format
            }
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd"))
                    .atStartOfDay(zone).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    /** The configured portal zone (default Asia/Seoul), cached. An invalid
     *  configured value falls back to the system default rather than failing
     *  every read. */
    private volatile ZoneId cachedZone;

    private ZoneId zone() {
        ZoneId z = cachedZone;
        if (z == null) {
            String cfg = knox.getDateZone();
            try {
                z = (cfg == null || cfg.isBlank())
                        ? ZoneId.systemDefault()
                        : ZoneId.of(cfg);
            } catch (Exception e) {
                log.warn("AI-NEWS-IMPORT invalid ai-news.knox.date-zone '{}' — "
                        + "falling back to system default zone", cfg);
                z = ZoneId.systemDefault();
            }
            cachedZone = z;
        }
        return z;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record DatedItem(Instant date, JsonNode item) {
    }
}
