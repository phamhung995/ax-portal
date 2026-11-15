package com.furelise.skillmanagement.mcp.service;

import com.furelise.skillmanagement.mcp.dto.McpCommentPage;
import com.furelise.skillmanagement.user.model.User;

/**
 * Comment operations (MCP Detail).
 */
public interface McpCommentService {

    /** Paged comments for an MCP (newest first, non-deleted). */
    McpCommentPage list(Long mcpId, User viewer, int page, int size);

    /** Adds a comment to a published MCP. */
    void add(Long mcpId, User user, String content);

    /** Soft-deletes a comment (own comment or admin). */
    void delete(Long mcpId, Long commentId, User user);
}
