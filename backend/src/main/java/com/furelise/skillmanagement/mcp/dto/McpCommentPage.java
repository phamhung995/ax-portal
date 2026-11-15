package com.furelise.skillmanagement.mcp.dto;

import java.util.List;

public record McpCommentPage(
        long totalCount,
        int page,
        int size,
        List<McpCommentItem> items
) {
}
