package com.furelise.skillmanagement.mcp.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * MCP grid search criteria (MCP Management SRS):
 * all fields optional; AND between fields, OR within a multi-value field.
 *
 * <p>categoryIds: the MCP must have at least one of the given categories.
 * stageId: the MCP must be mapped to that process stage.
 * subscribed: "Y" (viewer is subscribed), "N" (viewer is not), null = all.
 * sortBy: "name", "viewCount" or "avgRating" (per-MCP average, nulls last).
 *
 * <p>PUBLIC only — no scope/system filter fields.
 */
public record McpSearchRequest(
        String mcpName,
        String description,
        List<String> tags,
        Long creatorId,
        LocalDate createdDateFrom,
        LocalDate createdDateTo,
        List<String> statuses,
        List<Long> categoryIds,
        Long stageId,
        String subscribed,
        Integer page,
        Integer pageSize,
        String sortBy,
        String sortDirection
) {
}
