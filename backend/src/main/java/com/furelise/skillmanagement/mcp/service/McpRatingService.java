package com.furelise.skillmanagement.mcp.service;

import com.furelise.skillmanagement.mcp.dto.McpRatingResponse;
import com.furelise.skillmanagement.user.model.User;

/**
 * Star rating operations (MCP Detail).
 */
public interface McpRatingService {

    /** Aggregate rating for an MCP (average + count). */
    McpRatingResponse getRating(Long mcpId);

    /** The viewer's own rating for an MCP, or null if not yet rated. */
    Double getMyRating(Long mcpId, Long userId);

    /**
     * Upserts the viewer's rating (1.0-5.0, step 0.5).
     * The MCP must be published (APPROVED or version-update-pending).
     */
    McpRatingResponse upsertRating(Long mcpId, User user, double rating);
}
