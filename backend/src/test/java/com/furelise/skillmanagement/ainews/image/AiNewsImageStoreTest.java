package com.furelise.skillmanagement.ainews.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiNewsImageStoreTest {

    @TempDir
    Path tempDir;

    private AiNewsImageStore store;

    @BeforeEach
    void setUp() {
        store = new AiNewsImageStore(tempDir.toString(), "/uploads/ai-news-images");
    }

    @Test
    @DisplayName("stores a cid image and rewrites the img src to a local URL")
    void storesCidImage() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        String body = "<p>hi</p><img src=\"cid:img1\" />";

        String out = store.extractAndStore(body, 42L, Map.of("img1", png));

        assertThat(out).contains("/uploads/ai-news-images/42/");
        assertThat(out).doesNotContain("cid:img1");
        assertThat(out).contains("<p>hi</p>");
        List<Path> files = Files.list(tempDir.resolve("42")).toList();
        assertThat(files).hasSize(1);
        assertThat(files.get(0).toString()).endsWith(".img");
    }

    @Test
    @DisplayName("null/blank body is returned unchanged")
    void nullBody() {
        assertThat(store.extractAndStore(null, 1L, Map.of())).isNull();
        assertThat(store.extractAndStore("  ", 1L, Map.of())).isEqualTo("  ");
    }

    @Test
    @DisplayName("unknown cid is left untouched")
    void unknownCidUntouched() {
        String body = "<img src=\"cid:missing\" />";
        String out = store.extractAndStore(body, 1L, Map.of());
        assertThat(out).isEqualTo(body);
    }

    @Test
    @DisplayName("oversized cid image is rejected (bounded storage)")
    void oversizedCidRejected() throws Exception {
        byte[] big = new byte[AiNewsImageStore.MAX_IMAGE_BYTES + 1];
        String body = "<img src=\"cid:big\" />";
        String out = store.extractAndStore(body, 1L, Map.of("big", big));
        assertThat(out).isEqualTo(body);
        assertThat(Files.list(tempDir).count()).isZero();
    }

    @Test
    @DisplayName("deleteForNews removes the news image folder")
    void deleteForNews() {
        byte[] png = new byte[]{1, 2, 3};
        store.extractAndStore("<img src=\"cid:a\" />", 7L, Map.of("a", png));
        assertThat(Files.isDirectory(tempDir.resolve("7"))).isTrue();

        int removed = store.deleteForNews(7L);

        assertThat(removed).isEqualTo(1);
        assertThat(Files.exists(tempDir.resolve("7"))).isFalse();
    }

    @Test
    @DisplayName("deleteForNews on a missing folder returns 0")
    void deleteMissing() {
        assertThat(store.deleteForNews(99L)).isZero();
    }

    @Test
    @DisplayName("extension is inferred from the URL when content type is unknown")
    void extensionFromUrl() {
        assertThat(AiNewsImageStore.extensionFor("application/octet-stream",
                "http://x/a.png?1=1")).isEqualTo(".png");
        assertThat(AiNewsImageStore.extensionFor("image/jpeg", "x")).isEqualTo(".jpg");
        assertThat(AiNewsImageStore.extensionFor("", "x")).isEqualTo(".img");
    }

    @Test
    @DisplayName("svg content type is never mapped to .svg (stored-XSS guard)")
    void svgExtensionRejected() {
        // SVG can carry <script> and is served from the app origin — it must
        // fall through to the URL/fallback extension, never ".svg".
        assertThat(AiNewsImageStore.extensionFor("image/svg+xml", "x")).isEqualTo(".img");
        assertThat(AiNewsImageStore.extensionFor("image/svg+xml", "http://x/a.svg"))
                .isNotEqualTo(".svg");
    }

    @Test
    @DisplayName("stripDeadRemoteImages removes http/https imgs but keeps local and data URIs")
    void stripDeadRemoteImages() {
        String body = "<p>a</p><img src=\"http://kor5.samsung.net/x.png\">"
                + "<img src=\"https://example.com/y.png\">"
                + "<img src=\"/uploads/ai-news-images/1/a.png\">"
                + "<img src=\"data:image/png;base64,AAA\">";
        String out = store.stripDeadRemoteImages(body);
        assertThat(out).doesNotContain("http://kor5.samsung.net");
        assertThat(out).doesNotContain("https://example.com");
        assertThat(out).contains("/uploads/ai-news-images/1/a.png");
        assertThat(out).contains("data:image/png;base64,AAA");
        assertThat(out).contains("<p>a</p>");
    }

    @Test
    @DisplayName("stripDeadRemoteImages returns the input unchanged when no remote img exists")
    void stripDeadRemoteImagesNoRemote() {
        String body = "<p>a</p><img src=\"/uploads/ai-news-images/1/a.png\">";
        assertThat(store.stripDeadRemoteImages(body)).isEqualTo(body);
        assertThat(store.stripDeadRemoteImages(null)).isNull();
    }

    @Test
    @DisplayName("inlineOversizedCidImages replaces remaining cid: srcs with data URIs")
    void inlineOversizedCidImages() {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        String body = "<img src=\"cid:big.png\" />";
        String out = store.inlineOversizedCidImages(body, Map.of("big.png", png));
        assertThat(out).contains("data:image/png;base64,");
        assertThat(out).doesNotContain("cid:big.png");
        // no cid images → unchanged
        assertThat(store.inlineOversizedCidImages(body, Map.of())).isEqualTo(body);
        assertThat(store.inlineOversizedCidImages(body, null)).isEqualTo(body);
    }

    @Test
    @DisplayName("inlineOversizedCidImages skips images above the inline cap (body-size guard)")
    void inlineCidCap() {
        byte[] big = new byte[AiNewsImageStore.MAX_INLINE_CID_BYTES + 1];
        String body = "<img src=\"cid:huge.png\" />";
        // Too large to inline → left as cid: (the frontend onerror hides it)
        // instead of bloating the stored body with a multi-MB data URI.
        assertThat(store.inlineOversizedCidImages(body, Map.of("huge.png", big)))
                .isEqualTo(body);
    }
}
