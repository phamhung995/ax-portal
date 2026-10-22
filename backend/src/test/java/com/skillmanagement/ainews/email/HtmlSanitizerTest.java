package com.skillmanagement.ainews.email;

import com.skillmanagement.ainews.email.HtmlSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    @DisplayName("strips script tags and their content")
    void stripsScript() {
        String html = "<p>Hello</p><script>alert('xss')</script><p>World</p>";
        String out = sanitizer.sanitize(html);
        assertThat(out).doesNotContain("script");
        assertThat(out).doesNotContain("alert('xss')");
        assertThat(out).contains("<p>Hello</p>");
        assertThat(out).contains("<p>World</p>");
    }

    @Test
    @DisplayName("strips iframe, object, embed, form")
    void stripsDangerousElements() {
        String html = "<div>a</div><iframe src=\"http://evil\"></iframe>"
                + "<object data=\"x\"></object><embed src=\"y\"><form action=\"/x\"><input></form>";
        String out = sanitizer.sanitize(html);
        assertThat(out).doesNotContain("iframe");
        assertThat(out).doesNotContain("object");
        assertThat(out).doesNotContain("embed");
        assertThat(out).doesNotContain("form");
        assertThat(out).contains("<div>a</div>");
    }

    @Test
    @DisplayName("strips on* event handlers")
    void stripsEventHandlers() {
        String html = "<img src=\"a.png\" onclick=\"evil()\" onmouseover=\"bad()\"><a href=\"/x\" onfocus=\"x()\">link</a>";
        String out = sanitizer.sanitize(html);
        assertThat(out).doesNotContain("onclick");
        assertThat(out).doesNotContain("onmouseover");
        assertThat(out).doesNotContain("onfocus");
        assertThat(out).contains("a.png");
        assertThat(out).contains("link");
    }

    @Test
    @DisplayName("neutralizes javascript: and data: URLs")
    void neutralizesDangerousUrls() {
        String html = "<a href=\"javascript:alert(1)\">x</a><a href='data:text/html,<script>x</script>'>y</a>";
        String out = sanitizer.sanitize(html);
        assertThat(out).doesNotContain("javascript:");
        assertThat(out).doesNotContain("data:text/html");
        assertThat(out).doesNotContain("alert(1)");
    }

    @Test
    @DisplayName("keeps display markup (paragraphs, tables, links, images, lists)")
    void keepsDisplayMarkup() {
        String html = "<h2>Title</h2><p>Para</p><table><tr><td>Cell</td></tr></table>"
                + "<ul><li>Item</li></ul><a href=\"https://example.com\">link</a><img src=\"pic.png\">";
        String out = sanitizer.sanitize(html);
        assertThat(out).contains("<h2>Title</h2>");
        assertThat(out).contains("<p>Para</p>");
        assertThat(out).contains("<table>");
        assertThat(out).contains("<li>Item</li>");
        assertThat(out).contains("https://example.com");
        assertThat(out).contains("pic.png");
    }

    @Test
    @DisplayName("null/blank input returns null")
    void nullInput() {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("  ")).isNull();
    }

    @Test
    @DisplayName("plain text without tags is returned as-is")
    void plainText() {
        assertThat(sanitizer.sanitize("just text")).isEqualTo("just text");
    }
}
