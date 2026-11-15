package com.furelise.skillmanagement.mcp.util;

/**
 * Generates the PA-2 sequential short MCP code (e.g. {@code MCP-0001}).
 *
 * <p>The code is unique across the store and never reused after soft delete
 * (Q-2). It is generated once at creation by reading the max numeric suffix
 * from {@code mcp_master.mcp_code} and incrementing it.
 */
public final class McpCodeGenerator {

    /** Prefix for every MCP code (PA-2). */
    public static final String PREFIX = "MCP-";

    /** Zero-padded width of the numeric suffix. */
    public static final int WIDTH = 4;

    private McpCodeGenerator() {}

    /**
     * Formats the next code from the current max sequence value.
     *
     * @param maxSeq the highest numeric suffix seen so far (0 if none)
     * @return the next code, e.g. {@code MCP-0001} when {@code maxSeq == 0}
     */
    public static String next(int maxSeq) {
        return PREFIX + pad(maxSeq + 1);
    }

    private static String pad(int n) {
        StringBuilder sb = new StringBuilder(WIDTH);
        String s = Integer.toString(n);
        for (int i = s.length(); i < WIDTH; i++) {
            sb.append('0');
        }
        sb.append(s);
        return sb.toString();
    }
}
