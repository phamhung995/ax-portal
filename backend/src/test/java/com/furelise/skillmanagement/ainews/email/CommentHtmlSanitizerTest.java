package com.furelise.skillmanagement.ainews.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentHtmlSanitizerTest {

    private final CommentHtmlSanitizer sanitizer = new CommentHtmlSanitizer();

    @Test
    @DisplayName("keeps mention span with data-user-id / data-user-name")
    void keepsMentionSpan() {
        String html = "<p>Hi <span data-type=\"mention\" class=\"mention\" "
                + "data-user-id=\"12\" data-user-name=\"Nguyen Van A\">@Nguyen Van A</span>!</p>";
        String out = sanitizer.sanitize(html);
        assertThat(out).contains("data-type=\"mention\"");
        assertThat(out).contains("data-user-id=\"12\"");
        assertThat(out).contains("data-user-name=\"Nguyen Van A\"");
        assertThat(out).contains("@Nguyen Van A");
    }

    @Test
    @DisplayName("strips unknown data-* attributes from mention span")
    void stripsUnknownDataAttrs() {
        String html = "<span data-type=\"mention\" data-user-id=\"1\" data-evil=\"x\">@a</span>";
        String out = sanitizer.sanitize(html);
        assertThat(out).contains("data-user-id=\"1\"");
        assertThat(out).doesNotContain("data-evil");
    }

    @Test
    @DisplayName("drops script and event handlers")
    void stripsDangerousContent() {
        String html = "<p>x</p><script>alert(1)</script><img src=\"a.png\" onclick=\"evil()\">";
        String out = sanitizer.sanitize(html);
        assertThat(out).doesNotContain("script");
        assertThat(out).doesNotContain("alert(1)");
        assertThat(out).doesNotContain("onclick");
        assertThat(out).contains("<p>x</p>");
    }

    @Test
    @DisplayName("keeps http and base64 images, drops other src")
    void keepsValidImages() {
        String out = sanitizer.sanitize(
                "<img src=\"https://x.com/a.png\"><img src=\"data:image/png;base64,AAAA\">"
                        + "<img src=\"ftp://x\"><img src=\"data:text/html,abc\">");
        assertThat(out).contains("https://x.com/a.png");
        assertThat(out).contains("data:image/png;base64,AAAA");
        assertThat(out).doesNotContain("ftp://");
        assertThat(out).doesNotContain("data:text/html");
    }

    @Test
    @DisplayName("null/blank input returns null")
    void nullInput() {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("  ")).isNull();
    }
}
