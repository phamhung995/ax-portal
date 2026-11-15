package com.furelise.skillmanagement.mcp.dto;

import java.time.Instant;
import java.util.List;

/**
 * One row of the MCP Versions tab (a superseded, review-approved version).
 * The current version is the MCP itself and is not listed here.
 */
public record McpVersionItem(
        int version,
        String name,
        String description,
        List<String> hashtags,
        String creatorName,
        Instant createdAt,
        List<McpVersionFileItem> files
) {}
