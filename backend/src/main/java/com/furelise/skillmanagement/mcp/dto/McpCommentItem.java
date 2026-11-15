package com.furelise.skillmanagement.mcp.dto;

import java.time.Instant;

/** One comment row (MCP Detail). */
public record McpCommentItem(
        Long id,
        Long userId,
        String userName,
        String content,
        Instant createdAt,
        boolean canDelete
) {
}
