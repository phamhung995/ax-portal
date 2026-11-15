package com.furelise.skillmanagement.ainews.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.furelise.skillmanagement.ainews.batch.AiNewsBatchProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link KnoxPortalEmailReader} against a local
 * {@link HttpServer} that simulates the portal sign-in + mail list/read API
 * (the same contract probed from the portal SPA bundles). No live portal
 * access is needed.
 */
class KnoxPortalEmailReaderTest {

    private HttpServer server;
    private KnoxPortalEmailReader reader;
    private AiNewsBatchProperties properties;
    private final AtomicInteger loginCount = new AtomicInteger();
    private final AtomicInteger listCount = new AtomicInteger();
    /** false → the login endpoint answers NOT_EXIST_ID (bad credentials). */
    private volatile boolean loginSucceeds = true;
    /** true → the first list call 401s (session expired), then recovers. */
    private volatile boolean forceFirstList401 = false;
    /** false (default) → the mhtml/download endpoint 500s (fallback to detail/read).
     *  true → the mhtml/download endpoint returns a valid MIME message. */
    private volatile boolean mhtSucceeds = false;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/portal/signin/rest/login", exchange -> {
            loginCount.incrementAndGet();
            String status = loginSucceeds ? "SUCCESS" : "NOT_EXIST_ID";
            respond(exchange, 200, "{\"loginStatus\":\"" + status + "\"}");
        });
        server.createContext("/mail/rest/v1/mails/list", exchange -> {
            listCount.incrementAndGet();
            if (forceFirstList401) {
                forceFirstList401 = false; // only the first call 401s
                respond(exchange, 401, "{\"error\":\"session expired\"}");
                return;
            }
            respond(exchange, 200, LIST_RESPONSE);
        });
        server.createContext("/mail/rest/v1/mails/read", exchange -> {
            respond(exchange, 200,
                    "{\"mailID\":\"1001\",\"contentHtml\":\"<p>body of 1001</p>\"}");
        });
        server.createContext("/mail/rest/v1/mails/mhtml/download", exchange -> {
            if (!mhtSucceeds) {
                respond(exchange, 500, "{\"error\":\"mht unavailable\"}");
                return;
            }
            // mailSeq=2 → an auto-reply MHT (Auto-Submitted header) so the
            // test can verify the header flows through to the record.
            String body = exchange.getRequestURI().getQuery() != null
                    && exchange.getRequestURI().getQuery().contains("mailSeq=2")
                            ? MHT_BODY_AUTO_REPLY : MHT_BODY;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "message/rfc822");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        properties = new AiNewsBatchProperties();
        properties.getKnox().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getKnox().setLoginBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getKnox().setPageSize(50);
        reader = new KnoxPortalEmailReader(new ObjectMapper(), properties, new MhtEmailParser());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /** Three mails: two newer than the `since` marker (1001, 1002), one older
     *  (1003) — the older one must be filtered out. Field names mirror the
     *  live portal mail API contract (mailID, fromAddress, fromName, date,
     *  mailSeq, folderID). Dates carry an explicit Z so the test is
     *  independent of the configured portal zone (zone-less dates are
     *  interpreted in Asia/Seoul — see zonelessDateUsesTheGivenZone...). */
    private static final String LIST_RESPONSE = """
            {"headerVOs":[
              {"mailID":"1001","subject":"First news",
               "fromAddress":"gdc-tech@internal","fromName":"GDC Tech",
               "date":"2026-10-23T08:00:00Z","mailSeq":1,"folderID":1},
              {"mailID":"1002","subject":"Second news",
               "fromAddress":"gdc-tech@internal","fromName":"GDC Tech",
               "date":"2026-10-23T09:30:00Z","mailSeq":2,"folderID":1},
              {"mailID":"1003","subject":"Old mail",
               "fromAddress":"old@internal","fromName":"Old",
               "date":"2026-10-20T08:00:00Z","mailSeq":3,"folderID":1}
            ]}
            """;

    /** A valid MIME message/rfc822 (.mht) as returned by the portal's
     *  mhtml/download endpoint: real Message-ID, Auto-Submitted header, an
     *  HTML body and one inline CID image (1×1 PNG, base64). */
    private static final String MHT_BODY = """
            From: GDC Tech <gdc-tech@internal>
            To: news@internal
            Subject: MHT subject
            Date: Fri, 23 Oct 2026 17:00:00 +0900
            Message-ID: <mht-1001@internal>
            Auto-Submitted: auto-replied
            MIME-Version: 1.0
            Content-Type: multipart/related; boundary="BOUND"

            --BOUND
            Content-Type: text/html

            <p>html body from mht</p>
            --BOUND
            Content-Type: image/png; name="img.png"
            Content-Transfer-Encoding: base64
            Content-ID: <img1@internal>
            Content-Disposition: inline; filename="img.png"

            iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==
            --BOUND--
            """;

    /** Same as {@link #MHT_BODY} but with the auto-reply header and a
     *  distinct Message-ID (the mock returns this for mailSeq=2). */
    private static final String MHT_BODY_AUTO_REPLY = MHT_BODY
            .replace("Message-ID: <mht-1001@internal>", "Message-ID: <mht-1002@internal>")
            .replace("Auto-Submitted: auto-replied", "Auto-Submitted: auto-generated");

    @Test
    void readsNewerMailsSortedAscending() {
        Instant since = Instant.parse("2026-10-23T00:00:00Z");
        List<EmailRecord> emails = reader.read("user", "pass", "INBOX", since, 50, null);

        assertEquals(2, emails.size());
        // Oldest first — BR-NEWS-009.
        assertEquals("knox-1001", emails.get(0).messageId());
        assertEquals("knox-1002", emails.get(1).messageId());
        assertEquals("First news", emails.get(0).subject());
        assertEquals("GDC Tech <gdc-tech@internal>", emails.get(0).from());
        assertEquals("<p>body of 1001</p>", emails.get(0).bodyHtml());
        assertEquals(1, loginCount.get());
    }

    @Test
    void mhtDownloadYieldsRealMessageIdAndCidImages() {
        mhtSucceeds = true;
        Instant since = Instant.parse("2026-10-23T00:00:00Z");
        List<EmailRecord> emails = reader.read("user", "pass", "INBOX", since, 50, null);

        assertEquals(2, emails.size());
        // Real Message-ID from the MIME (not "knox-" + mailId) — dedup
        // BR-NEWS-002 now matches manual .mht imports.
        assertEquals("<mht-1001@internal>", emails.get(0).messageId());
        assertEquals("<mht-1002@internal>", emails.get(1).messageId());
        // List item's subject/sender stay canonical; the MIME Date header
        // (17:00 +0900 = 08:00Z) wins over the list item's date.
        assertEquals("First news", emails.get(0).subject());
        assertEquals("GDC Tech <gdc-tech@internal>", emails.get(0).from());
        assertEquals(Instant.parse("2026-10-23T08:00:00Z"), emails.get(0).date());
        // The inline CID image from the MIME parts reaches the record.
        assertEquals(1, emails.get(0).cidImages().size());
        assertTrue(emails.get(0).cidImages().containsKey("img1@internal"));
        // The HTML body comes from the MHT, not the detail call.
        assertEquals("<p>html body from mht</p>", emails.get(0).bodyHtml());
        // The Auto-Submitted header flows through (auto-reply filter §3.3).
        assertTrue(emails.get(0).isAutoReply());
        assertTrue(emails.get(1).isAutoReply());
    }

    @Test
    void mhtFailureFallsBackToDetailRead() {
        // mhtSucceeds defaults to false → the mhtml endpoint 500s; the
        // reader falls back to the plain detail (read) call.
        Instant since = Instant.parse("2026-10-23T00:00:00Z");
        List<EmailRecord> emails = reader.read("user", "pass", "INBOX", since, 50, null);

        assertEquals(2, emails.size());
        assertEquals("knox-1001", emails.get(0).messageId());
        assertEquals("<p>body of 1001</p>", emails.get(0).bodyHtml());
        // No images on the fallback path.
        assertTrue(emails.get(0).cidImages().isEmpty());
        // The list item's date is used (no MIME Date header available).
        assertEquals(Instant.parse("2026-10-23T08:00:00Z"), emails.get(0).date());
    }

    @Test
    void limitCapsTheResult() {
        Instant since = Instant.parse("2026-10-23T00:00:00Z");
        List<EmailRecord> emails = reader.read("user", "pass", "INBOX", since, 1, null);
        assertEquals(1, emails.size());
        assertEquals("knox-1001", emails.get(0).messageId());
    }

    @Test
    void emptyWhenNothingNewer() {
        Instant since = Instant.parse("2026-10-23T10:00:00Z");
        List<EmailRecord> emails = reader.read("user", "pass", "INBOX", since, 50, null);
        assertTrue(emails.isEmpty());
    }

    @Test
    void loginFailureThrowsWithStatus() {
        loginSucceeds = false;
        EmailReaderException e = assertThrows(EmailReaderException.class,
                () -> reader.read("user", "s3cret", "INBOX",
                        Instant.parse("2026-10-23T00:00:00Z"), 50, null));
        assertTrue(e.getMessage().contains("NOT_EXIST_ID"));
        // The password must never leak into the message (BR-NEWS-011).
        assertFalse(e.getMessage().contains("s3cret"));
    }

    @Test
    void sessionExpiryTriggersReloginAndRetry() {
        // First list call 401s (session expired); re-login restores it.
        forceFirstList401 = true;
        List<EmailRecord> emails = reader.read("user", "pass", "INBOX",
                Instant.parse("2026-10-23T00:00:00Z"), 50, null);
        assertEquals(2, emails.size());
        assertEquals(2, loginCount.get()); // initial + re-login
        assertEquals(2, listCount.get()); // 401 + retry
    }

    @Test
    void parseDateHandlesCommonFormats() {
        assertEquals(Instant.ofEpochSecond(1761234567L),
                KnoxPortalEmailReader.parseDate("1761234567"));
        assertEquals(Instant.ofEpochMilli(1761234567890L),
                KnoxPortalEmailReader.parseDate("1761234567890"));
        assertEquals(Instant.parse("2026-10-23T08:00:00Z"),
                KnoxPortalEmailReader.parseDate("2026-10-23T08:00:00Z"));
        assertNull(KnoxPortalEmailReader.parseDate(""));
        assertNull(KnoxPortalEmailReader.parseDate("not-a-date"));
    }

    @Test
    void zonelessDateUsesTheGivenZoneNotSystemDefault() {
        // 2026-10-23 08:00 KST (UTC+9) = 2026-10-22T23:00Z — independent of
        // the JVM default zone (prod runs UTC; a systemDefault() parse would
        // give 08:00Z, 9 hours off).
        java.time.ZoneId kst = java.time.ZoneId.of("Asia/Seoul");
        assertEquals(Instant.parse("2026-10-22T23:00:00Z"),
                KnoxPortalEmailReader.parseDate("2026-10-23 08:00:00", kst));
        assertEquals(Instant.parse("2026-10-22T23:00:00Z"),
                KnoxPortalEmailReader.parseDate("20261023080000", kst));
        assertEquals(Instant.parse("2026-10-22T23:00:00Z"),
                KnoxPortalEmailReader.parseDate("2026-10-23T08:00:00", kst));
    }

    @Test
    void deadlineExceededThrowsBeforeFetching() {
        // A deadline in the past must fail fast with a clear message (the
        // batch job maps that to a run-level FAILED, NFR-NEWS-01).
        EmailReaderException e = assertThrows(EmailReaderException.class,
                () -> reader.read("user", "pass", "INBOX",
                        Instant.parse("2026-10-23T00:00:00Z"), 50,
                        Instant.now().minusSeconds(60)));
        assertTrue(e.getMessage().contains("deadline exceeded"));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange,
                                int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
