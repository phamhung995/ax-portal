package com.furelise.skillmanagement.ainews.email;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * HTML sanitizer for imported email bodies (AI_News_Batch.md §4.3).
 *
 * <p>Uses Jsoup's whitelist-based sanitizer — a proper HTML parser that
 * reliably strips XSS vectors (script, iframe, on* handlers, javascript:
 * URLs, mutation XSS) unlike the previous regex approach. The sanitized
 * HTML is stored in {@code ai_news.body}; the client renders it with
 * {@code v-html} and CSP as a second defense layer (NFR-02).</p>
 *
 * <p>The whitelist is built on {@link Safelist#basicWithImages()} (inline
 * text + links + images + lists) plus the display markup that email bodies
 * rely on: headings, tables, and block quotes. {@code <style>} tags,
 * {@code <form>}/{@code <script>}/{@code <iframe>} are dropped. The
 * {@code class} attribute is preserved so the frontend can target elements
 * for styling overrides (see the {@code .ainews-body :deep(...)} rules in
 * AiNewsView.vue).</p>
 *
 * <p><b>Inline {@code style} attributes</b> (v1.14): preserved — email
 * bodies (Knox/Outlook) rely on ~100 inline style attributes for layout,
 * stripping them renders the email unstyled. The attribute value is
 * post-processed by {@link #sanitizeStyleValue(String)} which drops any
 * declaration containing a known-dangerous construct ({@code expression()},
 * {@code javascript:}/{@code vbscript:} URLs, {@code behavior:},
 * {@code -moz-binding}, {@code @import}). {@code <style>} blocks are still
 * dropped by the whitelist.</p>
 */
@Component
public class HtmlSanitizer {

    private static final Safelist WHITELIST = Safelist.basicWithImages()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "table", "thead", "tbody",
                    "tfoot", "tr", "th", "td", "caption", "blockquote", "ul", "ol", "li",
                    "div", "span", "hr", "br", "b", "i", "u", "s", "small", "sub", "sup")
            .addAttributes(":all", "style")
            .addAttributes("p", "align", "class")
            .addAttributes("table", "align", "border", "cellpadding", "cellspacing", "width", "class")
            .addAttributes("th", "align", "colspan", "rowspan", "class")
            .addAttributes("td", "align", "colspan", "rowspan", "class")
            .addAttributes("img", "width", "height", "class")
            .addAttributes("a", "class")
            // cid: src must survive sanitization — AiNewsImageStore rewrites
            // cid:XXX to local URLs (or data-URIs) AFTER sanitizing. A cid:
            // reference is inert in the browser (unresolvable → onerror hides
            // the img), so it is not an XSS vector.
            // data: src must also survive — desktop mail clients (Outlook)
            // embed images as data: URIs directly in the HTML (no CID parts).
            // A data: URI in an <img src> cannot execute script, so it is not
            // an XSS vector. (basicWithImages() only allows http/https.)
            .addProtocols("img", "src", "cid", "data");

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
        // Drop <style> blocks BEFORE whitelisting: the Samsung "cafe note"
        // mail editor embeds a full stylesheet (e.g. `body li, body p {
        // margin: 0 auto }`) that, once rendered via v-html, becomes a global
        // stylesheet for the whole app and breaks unrelated layouts.
        String noStyleBlocks = html.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        String cleaned = Jsoup.clean(noStyleBlocks, WHITELIST);
        return cleanStyleAttributes(cleaned);
    }

    /**
     * Drops dangerous constructs from inline {@code style} attribute values.
     * Operates on the already-whitelisted HTML (no script/iframe/form remain),
     * so only the style values themselves need checking.
     */
    private static String cleanStyleAttributes(String html) {
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parseBodyFragment(html);
        boolean changed = false;
        for (org.jsoup.nodes.Element el : doc.select("[style]")) {
            String original = el.attr("style");
            String safe = sanitizeStyleValue(original);
            if (!safe.equals(original)) {
                changed = true;
                if (safe.isBlank()) {
                    el.removeAttr("style");
                } else {
                    el.attr("style", safe);
                }
            }
        }
        return changed ? doc.body().html() : html;
    }

    /**
     * Removes declarations containing known-dangerous constructs from a CSS
     * declaration list: {@code expression(...)}, {@code javascript:}/
     * {@code vbscript:} URLs, {@code behavior:}, {@code -moz-binding},
     * {@code @import}, plus any declaration whose property can reposition,
     * stack, or resize the element beyond its content box
     * ({@code position}, {@code z-index}, {@code pointer-events},
     * {@code inset}/{@code top}/{@code left}/{@code right}/{@code bottom},
     * {@code width}/{@code height} + min/max, {@code transform}) — an
     * email body must not be able to overlay or swallow the app UI.
     * Case-insensitive; whitespace around colons is ignored.
     *
     * @param css a CSS declaration list (e.g. {@code "color: red; margin: 0"})
     * @return the css with dangerous declarations removed (may be blank)
     */
    static String sanitizeStyleValue(String css) {
        if (css == null || css.isBlank()) {
            return css;
        }
        StringBuilder out = new StringBuilder(css.length());
        for (String declaration : css.split(";")) {
            String d = declaration.trim();
            if (d.isEmpty() || isDangerousDeclaration(d)) {
                continue;
            }
            out.append(d).append(';');
        }
        return out.toString().trim();
    }

    private static boolean isDangerousDeclaration(String declaration) {
        String d = declaration.toLowerCase(java.util.Locale.ROOT);
        if (d.contains("expression(")
                || d.contains("javascript:")
                || d.contains("vbscript:")
                || d.contains("behavior:")
                || d.contains("-moz-binding")
                || d.contains("@import")
                || d.contains("url(")
                || d.contains("@")) {
            return true;
        }
        // Layout/stacking properties let an email body overlay the whole app
        // UI (position:fixed + inset:0 + z-index + opaque background = a
        // fake "session expired, re-login" screen rendered over the page for
        // every reader; pointer-events:none additionally swallows all clicks).
        // A blacklist of constructs is not exhaustive — block the properties
        // outright.
        int colon = d.indexOf(':');
        String property = (colon > 0 ? d.substring(0, colon) : d).trim();
        return DANGEROUS_CSS_PROPERTIES.contains(property);
    }

    /** CSS properties that can reposition/stack/resize an element beyond
     *  its content box — stripped from inline styles (see
     *  {@link #isDangerousDeclaration}). */
    private static final java.util.Set<String> DANGEROUS_CSS_PROPERTIES =
            java.util.Set.of(
                    "position", "z-index", "pointer-events",
                    "inset", "top", "left", "right", "bottom",
                    "width", "height", "min-width", "max-width",
                    "min-height", "max-height",
                    "transform", "transform-origin");
}
