package com.furelise.skillmanagement.ainews.email;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * HTML sanitizer for AI News comments (FR-23).
 *
 * <p>Comments are authored with the Tiptap rich text editor (same editor as
 * skill descriptions). The whitelist mirrors the frontend
 * {@code lib/sanitize.ts} so the stored HTML is exactly what the client
 * renders with {@code v-html}: inline text marks, headings, lists (incl.
 * task lists), tables, block quotes, code, links and images (http(s) or
 * base64 data-URIs of jpeg/png/webp).</p>
 *
 * <p>Unlike {@link HtmlSanitizer} (email bodies), inline {@code style}
 * attributes are NOT preserved — comments do not rely on layout styles,
 * and dropping them keeps the stored HTML small. {@code <script>},
 * {@code <iframe>}, {@code <form>} and {@code on*} handlers are dropped by
 * the whitelist; {@code javascript:} URLs are dropped by the protocol
 * allowlist.</p>
 */
@Component
public class CommentHtmlSanitizer {

    private static final Safelist WHITELIST = Safelist.basic()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "table", "thead", "tbody",
                    "tr", "th", "td", "colgroup", "col", "mark", "sub", "sup", "u",
                    "input", "label", "hr", "pre", "code", "span", "img")
            .addAttributes("a", "href")
            .addAttributes("img", "src", "alt", "width", "height")
            .addAttributes("input", "type", "checked", "disabled")
            .addAttributes("li", "data-type", "data-checked")
            .addAttributes("col", "width")
            .addAttributes("th", "colspan", "rowspan")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("span", "class", "data-type", "data-user-id", "data-user-name")
            .addProtocols("a", "href", "http", "https")
            .addProtocols("img", "src", "http", "https", "data");

    /**
     * Sanitizes a rich text comment.
     *
     * @param html raw HTML from the editor (may be null/blank)
     * @return sanitized HTML, or null when the input is null/blank
     */
    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String cleaned = Jsoup.clean(html, WHITELIST);
        Document doc = Jsoup.parseBodyFragment(cleaned);
        // The "data" protocol is broader than needed — keep only base64
        // image data-URIs (jpeg/png/webp), drop any other img src.
        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            boolean http = src.matches("^https?://\\S+$");
            boolean dataUri = src.matches("^data:image/(jpeg|png|webp);base64,\\S+$");
            if (!http && !dataUri) {
                img.remove();
            }
        }
        // Links open in a new tab (same policy as the frontend sanitizer).
        for (Element a : doc.select("a")) {
            a.attr("target", "_blank");
            a.attr("rel", "noopener");
        }
        // Task list checkboxes: only type="checkbox" survives.
        for (Element input : doc.select("input")) {
            if (!"checkbox".equals(input.attr("type"))) {
                input.remove();
            }
        }
        return doc.body().html();
    }
}
