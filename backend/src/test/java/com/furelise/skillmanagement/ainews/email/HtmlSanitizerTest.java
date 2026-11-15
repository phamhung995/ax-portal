package com.furelise.skillmanagement.ainews.email;

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
        // <div> is whitelisted (email layout) — the tag is kept.
        assertThat(out).contains("<div>");
        assertThat(out).contains("a");
    }

    @Test
    @DisplayName("strips on* event handlers")
    void stripsEventHandlers() {
        String html = "<img src=\"a.png\" onclick=\"evil()\" onmouseover=\"bad()\"><a href=\"/x\" onfocus=\"x()\">link</a>";
        String out = sanitizer.sanitize(html);
        assertThat(out).doesNotContain("onclick");
        assertThat(out).doesNotContain("onmouseover");
        assertThat(out).doesNotContain("onfocus");
        // img only whitelists width/height/class — src is dropped by the whitelist.
        assertThat(out).contains("<img>");
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
        // img only whitelists width/height/class — src is dropped by the whitelist.
        assertThat(out).contains("<img>");
    }

    @Test
    @DisplayName("keeps data: URIs in img src (desktop .mht embeds images as data-URIs)")
    void keepsDataUriInImgSrc() {
        String html = "<p>hi</p><img src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==\">";
        String out = sanitizer.sanitize(html);
        assertThat(out).contains("data:image/png;base64,");
        assertThat(out).contains("<img src=\"data:image/png;base64,");
    }

    @Test
    @DisplayName("keeps cid: URIs in img src (image store rewrites them after sanitize)")
    void keepsCidUriInImgSrc() {
        String html = "<img src=\"cid:part-1\">";
        String out = sanitizer.sanitize(html);
        assertThat(out).contains("cid:part-1");
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

    @Test
    @DisplayName("keeps safe inline style attributes")
    void keepsSafeInlineStyles() {
        String html = "<p style=\"color: red; margin: 0 auto\">text</p>"
                + "<table><tr><td style=\"background-color: #fff; padding: 4px\">cell</td></tr></table>";
        String out = sanitizer.sanitize(html);
        assertThat(out).contains("color: red");
        assertThat(out).contains("margin: 0 auto");
        assertThat(out).contains("background-color: #fff");
    }

    @Test
    @DisplayName("strips expression() from inline style")
    void stripsExpressionFromStyle() {
        String html = "<p style=\"width: expression(alert(1)); color: blue\">x</p>";
        String out = sanitizer.sanitize(html);
        assertThat(out).doesNotContain("expression(");
        assertThat(out).doesNotContain("alert(1)");
        assertThat(out).contains("color: blue");
    }

    @Test
    @DisplayName("strips javascript:/vbscript:/behavior from inline style")
    void stripsDangerousStyleConstructs() {
        String out = sanitizer.sanitize(
                "<a style=\"background: url(javascript:alert(1))\">a</a>"
                        + "<p style=\"behavior: url(x.htc)\">b</p>"
                        + "<p style=\"vbscript:msg(1)\">c</p>");
        assertThat(out).doesNotContain("javascript:");
        assertThat(out).doesNotContain("behavior:");
        assertThat(out).doesNotContain("vbscript:");
    }

    @Test
    @DisplayName("removes style attribute entirely when all declarations are dangerous")
    void removesStyleWhenAllDangerous() {
        String out = sanitizer.sanitize("<p style=\"width: expression(alert(1))\">x</p>");
        assertThat(out).doesNotContain("style");
    }

    @Test
    @DisplayName("still strips <style> blocks")
    void stripsStyleBlocks() {
        String html = "<style>body { color: red }</style><p style=\"color: blue\">x</p>";
        String out = sanitizer.sanitize(html);
        assertThat(out).doesNotContain("<style>");
        assertThat(out).contains("color: blue");
    }

    @Test
    @DisplayName("strips cafe-note stylesheet (body li/body p margin) but keeps surrounding content")
    void stripsCafeNoteStylesheet() {
        String html = "<p>before</p><style>body,html{overflow:visible!important;height:auto}"
                + "body{display:block;margin-left:24px}body li,body p{line-height:1.9;margin:0 auto}</style>"
                + "<p>after</p>";
        String out = sanitizer.sanitize(html);
        assertThat(out).doesNotContain("<style>");
        assertThat(out).doesNotContain("line-height:1.9");
        assertThat(out).doesNotContain("overflow:visible");
        assertThat(out).contains("<p>before</p>");
        assertThat(out).contains("<p>after</p>");
    }
}
