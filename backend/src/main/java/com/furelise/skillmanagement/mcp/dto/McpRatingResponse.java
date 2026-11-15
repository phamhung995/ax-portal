package com.furelise.skillmanagement.mcp.dto;

/** Aggregate rating for an MCP (grid/detail header). */
public record McpRatingResponse(double averageRating, long totalRatings) {
}
