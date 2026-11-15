package com.furelise.skillmanagement.mcp.dto;

/**
 * One file of a snapshotted MCP version (Versions tab).
 */
public record McpVersionFileItem(
        Long id,
        String fileName,
        Long fileSize
) {}
