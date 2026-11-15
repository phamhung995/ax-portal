package com.furelise.skillmanagement.mcp.dto;

import java.util.List;

/**
 * The staged (under review) version of an MCP — shown to the owner and
 * admins so they can review the proposed changes before approving.
 * The main detail content always shows the CURRENT (live) version.
 */
public record McpPendingVersionItem(
        String name,
        String description,
        List<String> hashtags,
        List<String> categories,
        List<String> stages,
        List<McpFileItem> newFiles,
        List<McpFileItem> removedFiles
) {}
