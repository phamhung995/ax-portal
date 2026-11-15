package com.furelise.skillmanagement.mcp.dto;

import java.time.Instant;
import java.util.List;

/**
 * MCP Detail popup payload. PUBLIC only — no scope/system/share/cowork fields.
 */
public record McpDetailResponse(
        Long id,
        String name,
        String description,
        String mcpCode,
        String serverKey,
        String status,
        String rejectionReason,
        List<String> hashtags,
        List<McpFileItem> files,
        int viewCount,
        long downloadCount,
        int version,
        Long creatorId,
        String creatorName,
        Instant createdAt,
        Instant updatedAt,
        Double avgRating,
        long ratingCount,
        boolean isCreator,
        boolean isAdmin,
        boolean canEdit,
        boolean subscribed,
        boolean hasPendingVersion,
        McpPendingVersionItem pendingVersion,
        List<String> categories,
        List<String> stages,
        /** "Skills using this MCP" tab payload (reverse reference). */
        List<DependentSkillItem> dependentSkills
) {
    /** One skill that pins this MCP (skill_mcp_dependency). */
    public record DependentSkillItem(
            Long skillId,
            String skillName,
            int skillVersion,
            String creatorName
    ) {}
}
