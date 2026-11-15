package com.furelise.skillmanagement.mcp.dto;

import java.util.List;

/**
 * Staged metadata for a pending version update on an APPROVED MCP.
 * Stored as JSONB in mcp_master.pending_metadata; applied to the MCP
 * when the update is approved. PUBLIC only — no scope/system fields.
 */
public record McpPendingMetadata(
        String name,
        String description,
        List<String> hashtags,
        List<Long> categoryIds,
        List<Long> stageIds
) {}
