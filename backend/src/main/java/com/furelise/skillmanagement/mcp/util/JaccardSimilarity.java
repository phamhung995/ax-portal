package com.furelise.skillmanagement.mcp.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Text similarity for fuzzy MCP suggestion (FR-MCP-013).
 *
 * <p>Q-45 v1.5 (Jaccard, threshold 0.3) — SUPERSEDED v1.26: weighted overlap
 * with a Unicode-aware tokenizer (Vietnamese diacritic-insensitive) and
 * stopword filtering.
 *
 * <ul>
 *   <li><b>Tokenizer (v1.26):</b> lowercase → NFD normalize + strip combining
 *       marks ("người" ≡ "nguoi") → split by non-Unicode-letter/digit
 *       ([^\\p{L}0-9]+) → dedupe → drop stopwords (EN/VI generic words).</li>
 *   <li><b>Jaccard</b> = |A ∩ B| / |A ∪ B| — used for name/tags (both sides
 *       comparable length).</li>
 *   <li><b>Overlap coefficient</b> = |A ∩ B| / min(|A|, |B|) — used for
 *       skill tokens vs MCP description (not biased when one side is much
 *       longer; answers "what fraction of the skill's keywords appear in the
 *       MCP").</li>
 * </ul>
 */
public final class JaccardSimilarity {

    /** Minimum final score to keep a suggestion (v1.26: 0.2 — PO: loosen while
     *  data is scarce, raise later as the MCP catalog grows). */
    public static final double THRESHOLD = 0.2;

    /** Weight for name/tags match (more trustworthy than description). */
    public static final double WEIGHT_NAME_TAGS = 0.6;

    /** Weight for description match. */
    public static final double WEIGHT_DESCRIPTION = 0.4;

    /** Generic words that carry no distinguishing signal (EN/VI).
     *  Vietnamese words are listed in diacritic-stripped form (tokenizer
     *  normalizes before lookup). */
    private static final Set<String> STOPWORDS = Set.of(
            // English
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "for",
            "with", "is", "are", "be", "by", "it", "its", "as", "at", "from",
            "data", "api", "service", "tools", "tool", "system", "application",
            "support", "using", "use", "into", "that", "this",
            // Vietnamese (stripped form)
            "va", "voi", "la", "de", "do", "di", "den", "tu", "cho",
            "vao", "ra", "can", "cach", "dang", "dieu", "dinh", "dong",
            "duoc", "giong", "khong", "khac", "khi", "mau", "moi", "mot",
            "nau", "nhau", "nhe", "nhat", "nhiu", "nha", "nhu", "qua",
            "roi", "sau", "sang", "suat", "tuc", "tun", "tuoi", "tren",
            "tuong", "ve", "xu");

    private JaccardSimilarity() {}

    /**
     * Computes the Jaccard similarity between two text blobs.
     *
     * @param a first text (e.g. skill name + description + hashtags)
     * @param b second text (e.g. MCP name + tags)
     * @return score in [0, 1]; 0 if either is empty
     */
    public static double score(String a, String b) {
        Set<String> ta = tokenize(a);
        Set<String> tb = tokenize(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(ta);
        intersection.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) intersection.size() / union.size();
    }

    /**
     * Overlap coefficient: |A ∩ B| / min(|A|, |B|).
     *
     * <p>Answers "what fraction of the smaller token set appears in the
     * other". Not biased by length difference — suitable for matching a short
     * skill name against a long MCP description.
     *
     * @param a first text (e.g. skill name + description + hashtags)
     * @param b second text (e.g. MCP description)
     * @return score in [0, 1]; 0 if either is empty
     */
    public static double overlap(String a, String b) {
        Set<String> ta = tokenize(a);
        Set<String> tb = tokenize(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(ta);
        intersection.retainAll(tb);
        int minSize = Math.min(ta.size(), tb.size());
        return (double) intersection.size() / minSize;
    }

    /**
     * Tokenizes text (v1.26): lowercase → NFD normalize + strip diacritics
     * ("người" → "nguoi") → split by non-Unicode-letter/digit → dedupe →
     * drop stopwords.
     */
    static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        // NFD: "người" → "ngư\u0301i" → strip combining marks → "nguoi"
        String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String[] parts = normalized.split("[^\\p{L}0-9]+");
        Set<String> tokens = new HashSet<>();
        for (String p : Arrays.asList(parts)) {
            if (!p.isEmpty() && !STOPWORDS.contains(p)) {
                tokens.add(p);
            }
        }
        return tokens;
    }
}
