package com.furelise.skillmanagement.skill.dto;

import java.util.List;

/**
 * One MCP dependency row on Skill Detail (FR-MCP-014/015, v1.31 tab "MCPs").
 *
 * <p>Q-39: uses {@code mcpId} (numeric) in request; {@code mcpCode} + {@code serverKey}
 * are display-only (returned in response).
 *
 * <p>v1.31: the per-dependency {@code required} flag is gone (all dependencies
 * are optional). The response carries everything the "MCPs (n)" tab needs:
 * status badge, whether the viewer can download the MCP
 * ({@code availableForViewer} = APPROVED + visible to viewer, OR deleted —
 * a deleted MCP's files stay downloadable so linked skills keep working),
 * and the files of the pinned version (only when downloadable).
 *
 * <p>Internal access: when the skill's scope is PRIVATE or PROJECT, an
 * unapproved MCP is still downloadable — the viewer already sees the skill
 * (internal audience), so the MCP approval gate (which protects PUBLIC
 * distribution) does not apply. {@code skillScope} carries the scope so the
 * frontend can render the correct UI.
 */
public record McpDependencyItem(
        Long mcpId,
        String mcpCode,
        String mcpName,
        String serverKey,
        Integer mcpVersion,
        String source,        // SKILL_MD | MANUAL
        String mcpStatus,     // MCP master status (for badge)
        boolean mcpDeleted,   // MCP soft-deleted — badge "deleted", still downloadable
        boolean availableForViewer, // APPROVED + visible (or deleted, or internal scope) → downloadable
        boolean mcpVisibleToViewer, // the MCP detail page is visible to the viewer (name link clickable)
        List<McpDependencyFileItem> files, // files of the pinned version (null when not available)
        String skillScope     // the skill's public scope (PRIVATE/PROJECT = internal access)
) {

    /** One downloadable file of the pinned MCP version (FR-MCP-012 endpoint). */
    public record McpDependencyFileItem(
            Long fileId,
            String fileRole,
            String fileName,
            Long fileSize
    ) {
    }
}
