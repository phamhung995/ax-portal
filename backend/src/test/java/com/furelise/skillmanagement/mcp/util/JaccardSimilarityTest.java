package com.furelise.skillmanagement.mcp.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JaccardSimilarity} (v1.26: weighted overlap,
 * threshold 0.2, Unicode-aware tokenizer with Vietnamese diacritic
 * stripping + stopword filter).
 */
class JaccardSimilarityTest {

    @Test
    void score_identicalTexts_isOne() {
        assertThat(JaccardSimilarity.score("jira ticket creator", "jira ticket creator")).isEqualTo(1.0);
    }

    @Test
    void score_partialOverlap_betweenZeroAndOne() {
        double s = JaccardSimilarity.score("jira ticket creator", "jira mcp server");
        // Tokens: {jira, ticket, creator} ∩ {jira, mcp, server} = {jira}
        // Union = {jira, ticket, creator, mcp, server} = 5
        // Score = 1/5 = 0.2
        assertThat(s).isEqualTo(0.2);
    }

    @Test
    void score_belowThreshold() {
        // {jira, ticket, creator} ∩ {jira, confluence, github, slack} = {jira}
        // union = 7 → 1/7 ≈ 0.143 < 0.2 threshold
        assertThat(JaccardSimilarity.score("jira ticket creator", "jira confluence github slack"))
                .isLessThan(JaccardSimilarity.THRESHOLD);
    }

    @Test
    void score_aboveThreshold() {
        // {jira, mcp} ∩ {jira, mcp, server} = 2, union = 3, score = 0.667
        double s = JaccardSimilarity.score("jira mcp", "jira mcp server");
        assertThat(s).isGreaterThan(JaccardSimilarity.THRESHOLD);
    }

    @Test
    void score_noOverlap_isZero() {
        assertThat(JaccardSimilarity.score("alpha beta", "gamma delta")).isEqualTo(0.0);
    }

    @Test
    void score_emptyText_isZero() {
        assertThat(JaccardSimilarity.score("", "something")).isEqualTo(0.0);
        assertThat(JaccardSimilarity.score(null, "something")).isEqualTo(0.0);
    }

    @Test
    void score_caseInsensitive() {
        assertThat(JaccardSimilarity.score("JIRA Ticket", "jira TICKET")).isEqualTo(1.0);
    }

    @Test
    void score_punctuationSplitIntoTokens() {
        // "jira-ticket" → {jira, ticket}
        assertThat(JaccardSimilarity.score("jira-ticket", "jira ticket")).isEqualTo(1.0);
    }

    @Test
    void score_deduplicatesTokens() {
        // "jira jira ticket" → {jira, ticket}
        assertThat(JaccardSimilarity.score("jira jira ticket", "jira ticket")).isEqualTo(1.0);
    }

    // ------------------------------------------------------------------
    // v1.26 — Vietnamese diacritic stripping
    // ------------------------------------------------------------------

    @Test
    void tokenize_stripsVietnameseDiacritics() {
        // "người vận chuyển" → {nguoi, van, chuyen}
        assertThat(JaccardSimilarity.tokenize("người vận chuyển"))
                .containsExactlyInAnyOrder("nguoi", "van", "chuyen");
    }

    @Test
    void score_vietnameseWithAndWithoutDiacritics_match() {
        // User types "nguoi van chuyen" (no diacritics) — must match
        // "Người vận chuyển" (with diacritics).
        assertThat(JaccardSimilarity.score("nguoi van chuyen", "Người vận chuyển")).isEqualTo(1.0);
    }

    @Test
    void overlap_vietnameseSkillName_matchesLongDescription() {
        // AC-MCP-045e: skill "người vận chuyển" vs MCP "transporter" whose
        // description contains "Người vận chuyển cho AIPRO — ..."
        String skill = "người vận chuyển";
        String mcpDesc = "Người vận chuyển cho AIPRO — biến channel Knox Teams thành chat box từ xa";
        // Skill tokens {nguoi, van, chuyen} all appear in the description
        // → overlap = 3/3 = 1.0 (Jaccard would be ~0.1 and fail).
        assertThat(JaccardSimilarity.overlap(skill, mcpDesc)).isEqualTo(1.0);
    }

    // ------------------------------------------------------------------
    // v1.26 — overlap coefficient
    // ------------------------------------------------------------------

    @Test
    void overlap_shortTextContainedInLongText_isOne() {
        // Not biased by length: "jira" (1 token) inside a 30-token text → 1.0
        String longText = "sync jira tickets to confluence and github with webhooks and automation";
        assertThat(JaccardSimilarity.overlap("jira", longText)).isEqualTo(1.0);
    }

    @Test
    void overlap_jaccardDivergesOnLengthDifference() {
        // Jaccard is diluted by the long side; overlap is not.
        String longText = "sync jira tickets to confluence and github with webhooks and automation";
        assertThat(JaccardSimilarity.score("jira", longText)).isLessThan(0.2);
        assertThat(JaccardSimilarity.overlap("jira", longText)).isEqualTo(1.0);
    }

    @Test
    void overlap_noOverlap_isZero() {
        assertThat(JaccardSimilarity.overlap("alpha beta", "gamma delta")).isEqualTo(0.0);
    }

    @Test
    void overlap_emptyText_isZero() {
        assertThat(JaccardSimilarity.overlap("", "something")).isEqualTo(0.0);
        assertThat(JaccardSimilarity.overlap(null, "something")).isEqualTo(0.0);
    }

    // ------------------------------------------------------------------
    // v1.26 — stopword filter
    // ------------------------------------------------------------------

    @Test
    void tokenize_dropsStopwords() {
        // "a" (EN) and "không", "tự" (VI) are generic → dropped
        assertThat(JaccardSimilarity.tokenize("a jira không tự confluence"))
                .containsExactlyInAnyOrder("jira", "confluence");
    }

    @Test
    void score_stopwordsDoNotCreateFalseMatch() {
        // "data service" (both stopwords) vs unrelated text → 0
        assertThat(JaccardSimilarity.score("data service", "jira ticket creator")).isEqualTo(0.0);
    }
}
