package com.furelise.skillmanagement.mcp.dto;

/**
 * Payload for renaming / re-describing an existing stage (admin).
 */
public record McpTaxonomyUpdateRequest(
        String name,
        String description
) {
}
