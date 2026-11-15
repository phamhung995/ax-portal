package com.furelise.skillmanagement.mcp.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FrontmatterParser} (Q-11, REV3-14).
 */
class FrontmatterParserTest {

    @Test
    void parse_inlineList_multipleEntries() {
        String md = """
                ---
                title: My Skill
                mcp: [jira-mcp@2, confluence-mcp@1?optional]
                ---
                # My Skill
                """;
        var entries = FrontmatterParser.parseMcpEntries(md);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).serverKey()).isEqualTo("jira-mcp");
        assertThat(entries.get(0).version()).isEqualTo(2);
        assertThat(entries.get(0).required()).isTrue();
        assertThat(entries.get(1).serverKey()).isEqualTo("confluence-mcp");
        assertThat(entries.get(1).version()).isEqualTo(1);
        assertThat(entries.get(1).optional()).isTrue();
        assertThat(entries.get(1).required()).isFalse();
    }

    @Test
    void parse_blockList_multipleEntries() {
        // Use plain string (not text block) to isolate any text block issues
        String md = "---\nmcp:\n  - jira-mcp@2\n  - confluence-mcp@1?optional\n---\n";
        var entries = FrontmatterParser.parseMcpEntries(md);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).serverKey()).isEqualTo("jira-mcp");
        assertThat(entries.get(0).version()).isEqualTo(2);
        assertThat(entries.get(0).required()).isTrue();
        assertThat(entries.get(1).serverKey()).isEqualTo("confluence-mcp");
        assertThat(entries.get(1).version()).isEqualTo(1);
        assertThat(entries.get(1).optional()).isTrue();
        assertThat(entries.get(1).required()).isFalse();
    }

    @Test
    void debug_extractFrontmatter_directly() {
        String md = "---\nmcp:\n  - jira-mcp@2\n  - confluence-mcp@1?optional\n---\n";
        String yaml = FrontmatterParser.extractFrontmatter(md);
        // Debug: if this fails, the issue is in extractFrontmatter
        assertThat(yaml).as("extractFrontmatter result").isNotNull();
        assertThat(yaml).as("yaml content").isEqualTo("mcp:\n  - jira-mcp@2\n  - confluence-mcp@1?optional");
    }

    @Test
    void debug_single_block_entry() {
        String md = "---\nmcp:\n  - test@1\n---\n";
        var entries = FrontmatterParser.parseMcpEntries(md);
        assertThat(entries).as("single block entry").hasSize(1);
    }

    @Test
    void debug_mcp_field_on_yaml() {
        // Directly test the yaml that extractFrontmatter returns
        String yaml = "mcp:\n  - test@1\n  - demo@2?optional";
        // Wrap in frontmatter to test via parseMcpEntries
        String md = "---\n" + yaml + "\n---\n";
        var entries = FrontmatterParser.parseMcpEntries(md);
        assertThat(entries).as("two block entries via wrapped yaml").hasSize(2);
    }

    @Test
    void parse_noFrontmatter_returnsEmpty() {
        assertThat(FrontmatterParser.parseMcpEntries("# Just a skill")).isEmpty();
    }

    @Test
    void parse_noMcpField_returnsEmpty() {
        String md = """
                ---
                title: My Skill
                ---
                content
                """;
        assertThat(FrontmatterParser.parseMcpEntries(md)).isEmpty();
    }

    @Test
    void parse_invalidEntry_skipped() {
        String md = """
                ---
                mcp: [valid-mcp@1, invalid-entry, also-bad@notanumber]
                ---
                """;
        var entries = FrontmatterParser.parseMcpEntries(md);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).serverKey()).isEqualTo("valid-mcp");
    }

    @Test
    void parse_nullContent_returnsEmpty() {
        assertThat(FrontmatterParser.parseMcpEntries(null)).isEmpty();
    }

    @Test
    void parse_emptyContent_returnsEmpty() {
        assertThat(FrontmatterParser.parseMcpEntries("")).isEmpty();
    }

    @Test
    void parse_quotedEntries_stripped() {
        String md = """
                ---
                mcp: ["jira-mcp@2", 'confluence-mcp@1']
                ---
                """;
        var entries = FrontmatterParser.parseMcpEntries(md);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).serverKey()).isEqualTo("jira-mcp");
        assertThat(entries.get(1).serverKey()).isEqualTo("confluence-mcp");
    }
}
