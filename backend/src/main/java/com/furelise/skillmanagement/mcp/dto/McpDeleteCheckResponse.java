package com.furelise.skillmanagement.mcp.dto;

import java.util.List;

/**
 * Pre-delete validation (grid bulk delete): the UI calls this before the
 * confirm dialog so it can show which selected MCPs cannot be deleted.
 */
public record McpDeleteCheckResponse(
        boolean canDelete,
        List<DeniedMcp> deniedMcps
) {
    public record DeniedMcp(Long id, String name, String creatorName) {
    }
}
