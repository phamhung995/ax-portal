package com.furelise.skillmanagement.mcp.dto;

/**
 * One row of the "Manage Categories & Stages" admin modal:
 * the master entry plus how many (not-deleted) MCPs use it.
 */
public record McpTaxonomyItem(
        Long id,
        String name,
        int sortOrder,
        String description,
        long mcpCount
) {
}
