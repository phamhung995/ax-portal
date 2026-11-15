package com.furelise.skillmanagement.mcp.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Generates the immutable {@code server_key} from an MCP name (Q-3, Q-33).
 *
 * <p>Algorithm (Q-33):
 * <ol>
 *   <li>Normalize Unicode NFC;</li>
 *   <li>lowercase;</li>
 *   <li>replace every run of non-[a-z0-9] characters with a single '-';</li>
 *   <li>collapse consecutive '-';</li>
 *   <li>trim leading/trailing '-';</li>
 *   <li>truncate to at most 50 characters;</li>
 *   <li>if empty → fallback {@code mcp-{mcpId}} (handled by the caller).</li>
 * </ol>
 *
 * <p>The server_key is generated once at creation and never changes even if the
 * owner renames the MCP (Q-3: locked forever).
 */
public final class ServerKeyGenerator {

    /** Max length of the slugified server_key (Q-33). */
    public static final int MAX_LENGTH = 50;

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern DASH_RUN = Pattern.compile("-{2,}");

    private ServerKeyGenerator() {}

    /**
     * Slugifies the given name into a server_key candidate (without the
     * empty-fallback, which needs the mcpId and is handled by the caller).
     *
     * @param name raw MCP name (may contain Unicode, spaces, punctuation)
     * @return slug candidate; empty string if the name yields nothing usable
     */
    public static String slugify(String name) {
        if (name == null) {
            return "";
        }
        // 1. NFC normalize
        String s = Normalizer.normalize(name, Normalizer.Form.NFC);
        // 2. lowercase
        s = s.toLowerCase(Locale.ROOT);
        // 3. replace non-[a-z0-9] runs with '-'
        s = NON_ALNUM.matcher(s).replaceAll("-");
        // 4. collapse consecutive '-'
        s = DASH_RUN.matcher(s).replaceAll("-");
        // 5. trim leading/trailing '-'
        s = s.replaceAll("^-+|-+$", "");
        // 6. truncate to MAX_LENGTH
        if (s.length() > MAX_LENGTH) {
            s = s.substring(0, MAX_LENGTH);
            // re-trim trailing '-' produced by truncation
            s = s.replaceAll("-+$", "");
        }
        return s;
    }

    /**
     * Builds the final server_key, applying the {@code mcp-{mcpId}} fallback
     * when the slugified name is empty (Q-33 step 7).
     */
    public static String generate(String name, Long mcpIdFallback) {
        String slug = slugify(name);
        if (slug.isEmpty()) {
            return "mcp-" + (mcpIdFallback == null ? 0 : mcpIdFallback);
        }
        return slug;
    }
}
