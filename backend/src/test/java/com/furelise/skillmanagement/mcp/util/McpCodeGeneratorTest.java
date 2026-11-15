package com.furelise.skillmanagement.mcp.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link McpCodeGenerator} (PA-2 sequential MCP-NNNN).
 */
class McpCodeGeneratorTest {

    @Test
    void next_from_zero_yields_first_code() {
        assertThat(McpCodeGenerator.next(0)).isEqualTo("MCP-0001");
    }

    @Test
    void next_increments_and_pads() {
        assertThat(McpCodeGenerator.next(1)).isEqualTo("MCP-0002");
        assertThat(McpCodeGenerator.next(9)).isEqualTo("MCP-0010");
        assertThat(McpCodeGenerator.next(99)).isEqualTo("MCP-0100");
        assertThat(McpCodeGenerator.next(999)).isEqualTo("MCP-1000");
    }

    @Test
    void next_handles_large_seq_without_overflow_width() {
        // Beyond 4 digits the padding stops growing; the number prints as-is.
        assertThat(McpCodeGenerator.next(9999)).isEqualTo("MCP-10000");
    }
}
