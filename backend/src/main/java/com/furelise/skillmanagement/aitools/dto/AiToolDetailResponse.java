package com.furelise.skillmanagement.aitools.dto;

import java.time.Instant;
import java.util.List;

/**
 * AI tool detail for the detail popup.
 */
public record AiToolDetailResponse(
        Long id,
        String name,
        String description,
        Long uploaderId,
        String uploaderName,
        Instant createdAt,
        Instant updatedAt,
        List<String> categories,
        List<String> stages,
        List<String> tags,
        long downloadCount,
        AiToolFileItem file,
        /** True when the viewer may edit/delete this tool (creator or admin). */
        boolean canEdit
) {
}
