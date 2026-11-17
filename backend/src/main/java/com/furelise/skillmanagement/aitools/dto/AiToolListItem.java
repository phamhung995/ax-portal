package com.furelise.skillmanagement.aitools.dto;

import java.time.Instant;
import java.util.List;

/**
 * One row of the AI Tools grid.
 */
public record AiToolListItem(
        Long id,
        String name,
        String description,
        Long uploaderId,
        String uploaderName,
        Instant createdAt,
        List<String> categories,
        List<String> stages,
        List<String> tags,
        long downloadCount,
        String fileName,
        Long fileId,
        /** True when the viewer may edit/delete this tool (creator or admin). */
        boolean canEdit,
        /** Cached reputation score (0-100, null = not enough downloads yet). */
        Double reputationScore,
        /** Cached reputation tier (NEW | TRUSTED | POPULAR). */
        String reputationTier
) {
}
