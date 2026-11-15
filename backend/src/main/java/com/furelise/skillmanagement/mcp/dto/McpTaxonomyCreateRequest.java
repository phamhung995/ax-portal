package com.furelise.skillmanagement.mcp.dto;

/**
 * Payload for creating a category or a stage (admin).
 */
public record McpTaxonomyCreateRequest(
        String name,
        String description
) {
}
