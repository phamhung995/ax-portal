package com.furelise.skillmanagement.mcp.dto;

import java.time.Instant;
import java.util.List;

/** One row of the MCP Management grid. */
public record McpListItem(
        Long id,
        String name,
        String description,
        String mcpCode,
        String serverKey,
        Long creatorId,
        String creatorName,
        Instant createdAt,
        String status,
        List<String> categories,
        List<String> stages,
        int viewCount,
        Double avgRating,
        long ratingCount,
        boolean subscribed,
        boolean hasPendingVersion,
        long downloadCount
) {
}
