package com.skillmanagement.ainews.email;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.springframework.stereotype.Component;

/**
 * HTML sanitizer for imported email bodies (AI_News_Batch.md §4.3).
 *
 * <p>Uses Jsoup's whitelist-based sanitizer — a proper HTML parser that
 * reliably strips XSS vectors (script, iframe, on* handlers, javascript:
 * URLs, mutation XSS) unlike the previous regex approach. The sanitized
 * HTML is stored in {@code ai_news.body}; the client renders it with
 * {@code v-html} and CSP as a second defense layer (NFR-02).
 *
 * <p>{@link Whitelist#relaxed()} keeps display markup (paragraphs, tables,
 * links, images, lists, headings) but drops {@code <style>} tags, inline
 * {@code style} attributes, and {@code <form>}/{@code <script>}/{@code
 * <iframe>}. The {@code class} attribute is preserved so the frontend
 * can target elements for styling overrides.
 */
@Component
public class HtmlSanitizer {

    private static final Whitelist WHITELIST = Whitelist.relaxed()
            .addAttributes(":all", "class");

    /**
     * Sanitizes an HTML email body.
     *
     * @param html raw HTML (may be null or blank)
     * @return sanitized HTML, or null when the input is null/blank
     */
    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        return Jsoup.clean(html, WHITELIST);
    }
}
