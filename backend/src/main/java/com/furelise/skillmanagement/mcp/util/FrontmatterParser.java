package com.furelise.skillmanagement.mcp.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the {@code mcp:} frontmatter field from a {@code skill.md} file
 * (Q-11: replaces the legacy manifest file skill.yaml/skill.json).
 *
 * <p>Supported syntax (v1.3 + REV3-14 v1.6):
 * <pre>
 *   ---
 *   mcp: [jira-mcp@2, confluence-mcp@1?optional]   // inline list
 *   ---
 * </pre>
 * or
 * <pre>
 *   ---
 *   mcp:
 *     - jira-mcp@2
 *     - confluence-mcp@1?optional
 *   ---
 * </pre>
 *
 * <p>Each entry is {@code server_key@version} with an optional {@code ?optional}
 * suffix (REV3-14: marks the dependency as required=false; default required=true).
 * Invalid entries are skipped with a warning (do not block upload — A4/E2).
 */
public final class FrontmatterParser {

    /** Captures the YAML frontmatter block delimited by --- lines. */
    private static final Pattern FRONTMATTER = Pattern.compile("(?s)^---\\s*\\n(.*?)\\n---\\s*\\n");

    /** Captures the mcp: field value (inline or first block entry). */
    private static final Pattern MCP_FIELD = Pattern.compile("(?m)^mcp:\\h*(.*)$");

    /** Parses one entry: server_key@version?optional. */
    private static final Pattern ENTRY = Pattern.compile("^([A-Za-z0-9][A-Za-z0-9._-]*)@(\\d+)(\\?optional)?$");

    private FrontmatterParser() {}

    /**
     * Parses the {@code mcp:} entries from a skill.md frontmatter.
     *
     * @param skillMdContent the full content of skill.md (or null/empty)
     * @return list of parsed entries (invalid entries are skipped); empty if
     *         no frontmatter or no mcp field
     */
    public static List<McpEntry> parseMcpEntries(String skillMdContent) {
        if (skillMdContent == null || skillMdContent.isBlank()) {
            return List.of();
        }
        // Extract frontmatter block manually (more robust than regex across
        // platforms/line endings): find first "---" at line start, then the
        // next "---" at line start.
        String yaml = extractFrontmatter(skillMdContent);
        if (yaml == null) {
            return List.of();
        }

        // Find the mcp: field
        Matcher mcpField = MCP_FIELD.matcher(yaml);
        if (!mcpField.find()) {
            return List.of();
        }
        String inlineValue = mcpField.group(1).trim();

        List<McpEntry> entries = new ArrayList<>();

        if (!inlineValue.isEmpty()) {
            // Inline list: mcp: [a@1, b@2?optional]
            String inner = inlineValue;
            if (inner.startsWith("[") && inner.endsWith("]")) {
                inner = inner.substring(1, inner.length() - 1);
            }
            for (String part : inner.split(",")) {
                parseEntry(part.trim(), entries);
            }
        } else {
            // Block list: scan subsequent lines for "- entry" pattern
            String[] lines = yaml.split("\\n");
            for (String line : lines) {
                String stripped = line.strip();
                if (stripped.startsWith("- ")) {
                    String entryStr = stripped.substring(2).trim();
                    if (!entryStr.contains("@")) {
                        continue;
                    }
                    parseEntry(entryStr, entries);
                }
            }
        }
        return entries;
    }

    /**
     * Extracts the YAML frontmatter block (between --- delimiters) manually.
     * Returns null if no valid frontmatter is found.
     */
    static String extractFrontmatter(String content) {
        // Normalize line endings
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        // First line must be "---"
        if (!normalized.startsWith("---")) {
            return null;
        }
        // Find the content start (after first "---" + newline)
        int start = 3;
        // Skip whitespace/newline after first ---
        while (start < normalized.length() && (normalized.charAt(start) == '\n' || normalized.charAt(start) == ' ')) {
            start++;
        }
        // Find the closing "---" (must be at line start)
        int end = normalized.indexOf("\n---", start);
        if (end == -1) {
            // Maybe the closing --- is right after content without a preceding \n
            return null;
        }
        return normalized.substring(start, end);
    }

    private static void parseEntry(String raw, List<McpEntry> entries) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        // Strip quotes if present
        String cleaned = raw.replaceAll("^['\"]|['\"]$", "");
        Matcher m = ENTRY.matcher(cleaned);
        if (m.matches()) {
            entries.add(new McpEntry(
                    m.group(1),           // serverKey
                    Integer.parseInt(m.group(2)), // version
                    m.group(3) != null));   // optional (?optional suffix)
        }
        // Invalid entry → skip (do not throw — A4/E2: fallback to fuzzy)
    }

    /** One parsed frontmatter entry: server_key@version[?optional]. */
    public record McpEntry(String serverKey, int version, boolean optional) {
        /** Required = true unless the ?optional suffix was present (REV3-14). */
        public boolean required() {
            return !optional;
        }
    }
}
