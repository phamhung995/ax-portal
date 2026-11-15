package com.furelise.skillmanagement.ainews.image;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Stores inline email images on disk so the sanitized body can reference
 * local URLs instead of mail-server temp URLs (which expire / are
 * user-scoped and return 403).
 *
 * <p>Layout: {@code {dir}/{newsId}/{sha256-16}{ext}}. Images are bounded:
 * max {@value #MAX_IMAGES_PER_EMAIL} per email, max {@value #MAX_IMAGE_BYTES}
 * bytes each — larger/extra images are left untouched (the frontend
 * onerror fallback hides them).</p>
 *
 * <p>Two image kinds are handled:</p>
 * <ul>
 *   <li>{@code cid:XXX} — MIME inline attachments (multipart/related),
 *       supplied by the caller from the raw message</li>
 *   <li>{@code http(s)://...} — external URLs, fetched with a short
 *       timeout; failures are left as-is</li>
 * </ul>
 */
@Component
@Slf4j
public class AiNewsImageStore {

    static final int MAX_IMAGES_PER_EMAIL = 20;
    static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    /**
     * Cap for base64-inlined CID images (inlineOversizedCidImages). The
     * base64 payload is embedded in the stored body HTML (DB + every API
     * response), so an uncapped inline would let one email bloat a news row
     * to hundreds of MB.
     */
    static final int MAX_INLINE_CID_BYTES = 4 * 1024 * 1024;
    /**
     * AGGREGATE cap for all inlined CID images of one email — 20 × 4MB each
     * would be ~107MB of base64 in a single stored row (returned in every
     * detail API response). 4MB total still covers a real email with one
     * large inline image while bounding the worst case.
     */
    static final int MAX_INLINE_CID_TOTAL_BYTES = 4 * 1024 * 1024;
    /** Cap on the per-hostname HTTPS client cache (see field javadoc). */
    static final int MAX_HTTPS_CLIENTS = 32;
    /** Per-image fetch timeout. 5s (was 10s) — the batch job fetches up to
     *  20 images per email serially, so the worst case was 200s/email;
     *  5s halves it to 100s while still covering slow-but-alive hosts.
     *  Dead hosts fail fast on connect (the SSRF guard's pre-resolve). */
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_REDIRECT_HOPS = 5;
    /** Parallel remote-image fetch workers. 20 images × 5s serial = 100s
     *  worst case; 4-way parallel ≈ 25s. */
    private static final int FETCH_PARALLELISM = 4;
    /** Hard cap on remote fetch ATTEMPTS per email (2× the store budget).
     *  The stored-count budget (MAX_IMAGES_PER_EMAIL) only counts successes,
     *  so without this an email with many dead remote images would fetch
     *  unboundedly. */
    private static final int MAX_REMOTE_FETCH_ATTEMPTS = MAX_IMAGES_PER_EMAIL * 2;

    /** Shared pool for parallel remote image fetches (daemon threads — the
     *  pool must not block JVM shutdown). */
    private final ExecutorService fetchPool = Executors.newFixedThreadPool(FETCH_PARALLELISM, r -> {
        Thread t = new Thread(r, "ai-news-image-fetch");
        t.setDaemon(true);
        return t;
    });

    private final Path rootDir;
    private final String urlPrefix;

    /**
     * Shared clients instead of one per redirect hop: the JDK's
     * {@code HttpClient} owns its connection pool + executor, so building a
     * new client per hop (up to 6 per image, up to 20 images per email)
     * wasted sockets and threads on every import. Plain HTTP needs no
     * per-host state → one shared client. HTTPS needs the SNI hostname
     * baked into the client's SSLParameters (the JDK has no per-request
     * SNI), so one client per distinct hostname, cached.
     */
    private final HttpClient plainHttpClient = HttpClient.newBuilder()
            .connectTimeout(FETCH_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    /**
     * Bounded LRU of per-hostname HTTPS clients. Hostnames come from
     * attacker-controlled email HTML — an unbounded map would leak one
     * client (connection pool + executor threads) per distinct hostname
     * for the JVM's lifetime. Evicted clients are closed (JDK 21
     * HttpClient is AutoCloseable).
     */
    private final Map<String, HttpClient> httpsClientsByHost =
            java.util.Collections.synchronizedMap(
                    new java.util.LinkedHashMap<>(16, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(java.util.Map.Entry<String, HttpClient> eldest) {
                            if (size() <= MAX_HTTPS_CLIENTS) {
                                return false;
                            }
                            try {
                                eldest.getValue().close();
                            } catch (Exception e) {
                                log.debug("AI-NEWS-IMPORT closing evicted HTTPS client for {}: {}",
                                        eldest.getKey(), e.getMessage());
                            }
                            return true;
                        }
                    });

    public AiNewsImageStore(
            @Value("${app.upload.ai-news-image-dir:uploads/ai-news-images/}") String imageDir,
            @Value("${app.upload.ai-news-image-url-prefix:/uploads/ai-news-images}") String imageUrlPrefix) {
        this.rootDir = Path.of(imageDir).toAbsolutePath().normalize();
        this.urlPrefix = imageUrlPrefix.endsWith("/")
                ? imageUrlPrefix.substring(0, imageUrlPrefix.length() - 1)
                : imageUrlPrefix;
    }

    /**
     * Extracts inline images from the (sanitized) body HTML, stores them
     * under {@code {rootDir}/{newsId}/} and rewrites the {@code <img src>}
     * attributes to local URLs.
     *
     * @param bodyHtml  sanitized body HTML (may be null)
     * @param newsId    ai_news.news_id (folder name)
     * @param cidImages cid → bytes map from the MIME message (may be empty)
     * @return the body HTML with local image URLs, or the input when null/blank
     */
    public String extractAndStore(String bodyHtml, Long newsId, Map<String, byte[]> cidImages) {
        if (bodyHtml == null || bodyHtml.isBlank() || newsId == null) {
            return bodyHtml;
        }
        Document doc = Jsoup.parse(bodyHtml);
        java.util.List<Element> imgs = doc.select("img[src]");
        int stored = 0;
        // CID images are local (in-memory bytes) — store them inline. Remote
        // images are collected and fetched in parallel afterwards.
        java.util.List<Element> remoteImgs = new java.util.ArrayList<>();
        for (Element img : imgs) {
            if (stored >= MAX_IMAGES_PER_EMAIL) {
                break;
            }
            String src = img.attr("src");
            // Case-insensitive prefix: some mail clients emit "CID:".
            if (src.regionMatches(true, 0, "cid:", 0, 4)) {
                String localUrl = storeCid(newsId, src.substring(4), cidImages);
                if (localUrl != null) {
                    img.attr("src", localUrl);
                    stored++;
                }
            } else if (src.startsWith("http://") || src.startsWith("https://")) {
                remoteImgs.add(img);
            }
        }
        if (!remoteImgs.isEmpty()) {
            int budget = MAX_IMAGES_PER_EMAIL - stored;
            java.util.List<Element> toFetch = remoteImgs.subList(0,
                    Math.min(remoteImgs.size(), MAX_REMOTE_FETCH_ATTEMPTS));
            Map<Element, String> results = fetchRemoteParallel(newsId, toFetch);
            for (Element img : toFetch) {
                String localUrl = results.get(img);
                if (localUrl != null && budget > 0) {
                    img.attr("src", localUrl);
                    stored++;
                    budget--;
                }
            }
        }
        return stored > 0 ? doc.body().html() : bodyHtml;
    }

    /**
     * Fetches the remote images in parallel (small fixed pool). A failing
     * image is skipped (null) — one dead image never breaks the import.
     *
     * @return img element → local URL (only successful fetches)
     */
    private Map<Element, String> fetchRemoteParallel(long newsId, java.util.List<Element> imgs) {
        Map<Element, String> results = new HashMap<>();
        java.util.List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>(imgs.size());
        for (Element img : imgs) {
            futures.add(fetchPool.submit(() -> storeRemote(newsId, img.attr("src"))));
        }
        boolean interrupted = false;
        for (int i = 0; i < futures.size(); i++) {
            try {
                String localUrl = futures.get(i).get();
                if (localUrl != null) {
                    results.put(imgs.get(i), localUrl);
                }
            } catch (InterruptedException e) {
                // The batch job's interrupt (timeout path) — stop collecting,
                // cancel the remaining fetches, restore the flag.
                interrupted = true;
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // storeRemote catches its own failures; an executor-level
                // failure (e.g. rejection) just skips this image.
                log.debug("AI-NEWS-IMPORT image fetch task failed: {}", e.getMessage());
            }
        }
        if (interrupted) {
            for (java.util.concurrent.Future<String> f : futures) {
                f.cancel(true);
            }
        }
        return results;
    }

    /**
     * Deletes the image folder of an archived news item (best-effort).
     *
     * @return number of files removed (0 when the folder does not exist)
     */
    public int deleteForNews(Long newsId) {
        if (newsId == null) {
            return 0;
        }
        Path dir = rootDir.resolve(String.valueOf(newsId));
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int removed = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (Files.deleteIfExists(file)) {
                    removed++;
                }
            }
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            log.warn("AI-NEWS-IMPORT image cleanup for news {} failed: {}", newsId, e.getMessage());
            return 0;
        }
        return removed;
    }

    private String storeCid(long newsId, String cid, Map<String, byte[]> cidImages) {
        byte[] bytes = cidImages.get(cid);
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            return null;
        }
        String ext = extensionFor("image", cid);
        return storeBytes(newsId, "cid-" + cid, bytes, ext);
    }

    private String storeRemote(long newsId, String url) {
        try {
            HttpResponse<InputStream> response = fetchWithSsrfGuard(URI.create(url));
            if (response == null) {
                return null;
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (contentType.toLowerCase().contains("svg")) {
                // SVG can carry <script> and is served from the app origin —
                // never store it (see extensionFor).
                response.body().close();
                return null;
            }
            byte[] bytes;
            try (InputStream in = response.body()) {
                bytes = in.readNBytes(MAX_IMAGE_BYTES + 1);
            }
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                return null;
            }
            String ext = extensionFor(contentType, url);
            return storeBytes(newsId, url, bytes, ext);
        } catch (InterruptedException e) {
            // Restore the interrupt flag — the batch job's timeout/interrupt
            // path relies on it to actually stop the run.
            Thread.currentThread().interrupt();
            log.debug("AI-NEWS-IMPORT image fetch interrupted for {}", url);
            return null;
        } catch (Exception e) {
            // Best-effort: a failing image must never break the import.
            log.debug("AI-NEWS-IMPORT image fetch failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches a remote image URL, following redirects manually so that every
     * hop's target host is validated against the SSRF guard. Email bodies are
     * attacker-controlled, so an unvalidated fetch could be pointed at
     * internal hosts (loopback, RFC1918, link-local, cloud metadata) to read
     * internal services or exfiltrate data.
     *
     * <p>Each hop connects to the VALIDATED IP address (pinned via a
     * per-hop {@link HttpClient.DnsResolver} that maps the hop's hostname to
     * the validated address), not to whatever DNS returns at connect time:
     * the JDK client re-resolves DNS independently, so a plain
     * check-then-fetch is bypassable by DNS rebinding (TTL-0 records
     * returning a public IP for the validation lookup and an internal IP for
     * the connect). The original URI (hostname, scheme) is kept so TLS SNI
     * and certificate validation still work.</p>
     *
     * @return the 200 response for the final hop, or {@code null} when the
     *         URL is blocked, exceeds the redirect budget, or returns non-200
     */
    private HttpResponse<InputStream> fetchWithSsrfGuard(URI start) throws Exception {
        URI current = start;
        for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
            InetAddress pinned = resolvePublicAddress(current);
            if (pinned == null) {
                log.debug("AI-NEWS-IMPORT image fetch blocked (non-public host): {}", current);
                return null;
            }
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(FETCH_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = sendPinned(current.getHost(), pinned, request);
            int status = response.statusCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = response.headers().firstValue("Location").orElse(null);
                response.body().close();
                if (location == null) {
                    return null;
                }
                current = current.resolve(location);
                continue;
            }
            if (status != 200) {
                response.body().close();
                return null;
            }
            return response;
        }
        log.debug("AI-NEWS-IMPORT image fetch blocked (too many redirects): {}", start);
        return null;
    }

    /**
     * Sends the request to the VALIDATED IP address (as the URI host) with
     * the original hostname set as the TLS SNI — the connection therefore
     * cannot be redirected to an internal address by a second DNS lookup
     * (DNS rebinding), while the certificate is still validated against the
     * real hostname (the JDK's default hostname verification uses the SNI
     * when the URI host is an IP).
     *
     * <p>The JDK's {@code HttpClient.Builder.dnsResolver} (the natural fit)
     * is not available on this runtime (JDK 24+), so the IP-host + SNI
     * approach is used instead — verified empirically: the JDK verifies the
     * peer certificate against the SNI hostname, not the IP host.</p>
     */
    private HttpResponse<InputStream> sendPinned(String hostname, InetAddress pinned,
                                                 HttpRequest request) throws Exception {
        URI original = request.uri();
        int port = original.getPort() != -1
                ? original.getPort()
                : (original.getScheme().equalsIgnoreCase("https") ? 443 : 80);
        String ipHost = pinned instanceof java.net.Inet6Address
                ? "[" + pinned.getHostAddress() + "]"
                : pinned.getHostAddress();
        URI pinnedUri = new URI(original.getScheme(), null, ipHost, port,
                original.getPath(), original.getQuery(), null);
        HttpRequest.Builder builder = HttpRequest.newBuilder(pinnedUri)
                .timeout(FETCH_TIMEOUT)
                .GET();
        // Copy the original request headers (the image fetch sends none
        // beyond what the builder adds, but keep parity).
        request.headers().map().forEach((name, values) ->
                values.forEach(value -> {
                    if (!"Host".equalsIgnoreCase(name)) {
                        builder.header(name, value);
                    }
                }));
        return httpClientFor(original, hostname)
                .send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    /**
     * Client for the pinned request: the shared plain-HTTP client, or a
     * per-hostname HTTPS client (SNI is baked into the client's
     * SSLParameters — the JDK has no per-request SNI).
     */
    private HttpClient httpClientFor(URI original, String hostname) {
        if (!original.getScheme().equalsIgnoreCase("https")) {
            return plainHttpClient;
        }
        return httpsClientsByHost.computeIfAbsent(hostname, host -> {
            SSLParameters params = new SSLParameters();
            params.setServerNames(List.of(new SNIHostName(host)));
            return HttpClient.newBuilder()
                    .connectTimeout(FETCH_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .sslParameters(params)
                    .build();
        });
    }

    /**
     * Resolves the host of a URL and returns a public unicast address to pin
     * the connection to, or {@code null} when the host is not a public
     * unicast address: loopback, site-local (RFC1918), link-local
     * (169.254.x, incl. cloud metadata), any-local, and multicast.
     */
    private static InetAddress resolvePublicAddress(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return null;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                // IPv4-mapped IPv6 literal (e.g. [::ffff:169.254.169.254]) —
                // unwrap and check the embedded IPv4: the isLoopback/
                // isSiteLocal/isLinkLocal checks below return FALSE for a
                // mapped address, which would let the JVM connect to the
                // internal IPv4 target (SSRF bypass).
                if (address instanceof java.net.Inet6Address v6
                        && v6.isIPv4CompatibleAddress()) {
                    byte[] b = v6.getAddress();
                    address = InetAddress.getByAddress(
                            new byte[]{b[12], b[13], b[14], b[15]});
                }
                if (address.isLoopbackAddress()
                        || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress()
                        || address.isMulticastAddress()) {
                    return null;
                }
                // IPv6 Unique Local Address (fc00::/7) — not covered by
                // isSiteLocalAddress (which is IPv4 10/172.16/192.168 only).
                if (address instanceof java.net.Inet6Address addr6) {
                    byte[] b = addr6.getAddress();
                    if ((b[0] & 0xFE) == 0xFC) {
                        return null;
                    }
                }
                // CGNAT 100.64.0.0/10 — not covered by isSiteLocalAddress.
                if (address instanceof java.net.Inet4Address addr4) {
                    byte[] b = addr4.getAddress();
                    if ((b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64) {
                        return null;
                    }
                }
            }
            return addresses.length > 0 ? addresses[0] : null;
        } catch (Exception e) {
            // Unresolvable host — treat as blocked (the fetch would fail anyway).
            return null;
        }
    }

    private String storeBytes(long newsId, String key, byte[] bytes, String ext) {
        try {
            Path dir = rootDir.resolve(String.valueOf(newsId));
            Files.createDirectories(dir);
            String name = sha256(key).substring(0, 16) + ext;
            Path file = dir.resolve(name);
            Files.write(file, bytes);
            return urlPrefix + "/" + newsId + "/" + name;
        } catch (IOException e) {
            log.warn("AI-NEWS-IMPORT image store failed for news {} ({}): {}",
                    newsId, key, e.getMessage());
            return null;
        }
    }

    /**
     * Replaces remaining {@code <img src="cid:...">} references (images the
     * store skipped for size) with base64 data-URIs so the body renders
     * completely. Shared by the .mht import and the Knox portal batch pipeline.
     *
     * @param bodyHtml  body HTML (may be null)
     * @param cidImages cid → bytes map (may be null/empty)
     * @return the body HTML with data-URI images, or the input when unchanged
     */
    public String inlineOversizedCidImages(String bodyHtml, Map<String, byte[]> cidImages) {
        if (bodyHtml == null || cidImages == null || cidImages.isEmpty()) {
            return bodyHtml;
        }
        Document doc = Jsoup.parseBodyFragment(bodyHtml);
        boolean changed = false;
        int inlined = 0;
        // Aggregate cap: the sum of ALL inlined images of one email is bounded
        // (MAX_INLINE_CID_TOTAL_BYTES) — 20 × 4MB each would still be ~107MB
        // of base64 in a single stored row.
        long totalInlinedBytes = 0;
        // Jsoup attribute selectors are case-sensitive on values — some mail
        // clients emit "CID:", so scan all imgs and match the prefix
        // case-insensitively.
        for (Element img : doc.select("img[src]")) {
            String src = img.attr("src");
            if (!src.regionMatches(true, 0, "cid:", 0, 4)) {
                continue;
            }
            // Per-email count cap — prevents unbounded base64 inlining that
            // would bloat the stored body (returned in every detail API
            // response). Matches MAX_IMAGES_PER_EMAIL in extractAndStore.
            if (inlined >= MAX_IMAGES_PER_EMAIL) {
                break;
            }
            String cid = src.substring(4);
            byte[] bytes = cidImages.get(cid);
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_INLINE_CID_BYTES) {
                // Oversized images are left as-is (the frontend onerror
                // fallback hides them) rather than bloating the stored body.
                continue;
            }
            if (totalInlinedBytes + bytes.length > MAX_INLINE_CID_TOTAL_BYTES) {
                // Aggregate budget exhausted — leave the remaining cid: srcs
                // as-is (the frontend onerror fallback hides them).
                continue;
            }
            img.attr("src", "data:" + contentTypeFor(cid) + ";base64,"
                    + java.util.Base64.getEncoder().encodeToString(bytes));
            changed = true;
            inlined++;
            totalInlinedBytes += bytes.length;
        }
        return changed ? doc.body().html() : bodyHtml;
    }

    /**
     * Removes {@code <img>} tags whose src is still a remote http(s) URL —
     * images the store could not fetch (e.g. Knox temp URLs returning 403).
     * A dead remote image can never render (the host is unreachable and the
     * frontend CSP blocks http:), so drop the tag instead of leaving a broken
     * image in the body.
     *
     * @param bodyHtml  body HTML (may be null)
     * @return the body HTML without dead remote images, or the input when unchanged
     */
    public String stripDeadRemoteImages(String bodyHtml) {
        if (bodyHtml == null) {
            return bodyHtml;
        }
        Document doc = Jsoup.parseBodyFragment(bodyHtml);
        Elements remote = doc.select("img[src^=http://], img[src^=https://]");
        if (remote.isEmpty()) {
            return bodyHtml;
        }
        remote.remove();
        return doc.body().html();
    }

    private static String contentTypeFor(String cid) {
        String lower = cid.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/png";
    }

    /** File extension from a MIME content type, falling back to the URL/cid path. */
    static String extensionFor(String contentType, String key) {
        String ct = contentType.toLowerCase();
        if (ct.contains("png")) return ".png";
        if (ct.contains("jpeg") || ct.contains("jpg")) return ".jpg";
        if (ct.contains("gif")) return ".gif";
        if (ct.contains("webp")) return ".webp";
        // SVG is intentionally NOT mapped: an SVG file can carry <script> and
        // is served from the app origin (permitAll static path) — storing one
        // would be a stored-XSS vector. storeRemote rejects SVG content types.
        if (ct.contains("bmp")) return ".bmp";
        // Fallback: extension of the key (URL path or cid filename), if image-like.
        String path = key;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        if (dot > slash && dot < path.length() - 1) {
            String ext = path.substring(dot).toLowerCase();
            if (ext.equals(".png") || ext.equals(".jpg") || ext.equals(".jpeg")
                    || ext.equals(".gif") || ext.equals(".webp") || ext.equals(".bmp")) {
                return ext;
            }
        }
        return ".img";
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
