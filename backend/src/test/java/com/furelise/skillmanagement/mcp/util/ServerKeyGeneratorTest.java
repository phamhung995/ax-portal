package com.furelise.skillmanagement.mcp.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ServerKeyGenerator} (Q-33: NFC + max 50 + fallback).
 */
class ServerKeyGeneratorTest {

    @Test
    void slugify_basic() {
        assertThat(ServerKeyGenerator.slugify("Tavily Search")).isEqualTo("tavily-search");
    }

    @Test
    void slugify_unicode_nfc() {
        // Vietnamese diacritics normalized via NFC then stripped to [a-z0-9]
        assertThat(ServerKeyGenerator.slugify("Tìm kiếm Jira")).isEqualTo("t-m-ki-m-jira");
    }

    @Test
    void slugify_punctuation_replaced_with_dash() {
        assertThat(ServerKeyGenerator.slugify("GitHub MCP! Server")).isEqualTo("github-mcp-server");
    }

    @Test
    void slugify_collapses_consecutive_dashes() {
        assertThat(ServerKeyGenerator.slugify("a---b")).isEqualTo("a-b");
    }

    @Test
    void slugify_trims_leading_trailing_dashes() {
        assertThat(ServerKeyGenerator.slugify("--hello--")).isEqualTo("hello");
    }

    @Test
    void slugify_truncates_to_50_chars() {
        String longName = "a".repeat(120);
        String slug = ServerKeyGenerator.slugify(longName);
        assertThat(slug).hasSizeLessThanOrEqualTo(50);
    }

    @Test
    void slugify_empty_when_no_alnum() {
        assertThat(ServerKeyGenerator.slugify("!!! ??? ---")).isEmpty();
    }

    @Test
    void generate_falls_back_to_mcp_id_when_empty() {
        assertThat(ServerKeyGenerator.generate("!!!", 42L)).isEqualTo("mcp-42");
    }

    @Test
    void generate_returns_slug_when_nonempty() {
        assertThat(ServerKeyGenerator.generate("Tavily Search", 1L)).isEqualTo("tavily-search");
    }

    @Test
    void slugify_lowercase_only() {
        assertThat(ServerKeyGenerator.slugify("MixedCASE")).isEqualTo("mixedcase");
    }
}
