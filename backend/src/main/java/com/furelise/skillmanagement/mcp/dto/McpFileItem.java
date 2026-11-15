package com.furelise.skillmanagement.mcp.dto;

import java.time.Instant;

/**
 * A file attached to an MCP (detail popup + edit popup).
 */
public record McpFileItem(
        Long id,
        String fileName,
        Long fileSize,
        Instant createdAt
) {
}
