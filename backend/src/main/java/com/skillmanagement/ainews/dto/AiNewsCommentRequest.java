package com.skillmanagement.ainews.dto;

/**
 * POST /api/v1/home/ai-news/{newsId}/comments body (AI_News_Screen_SRS.md §5.6).
 * parentId = null → top-level; non-null → reply (depth <= 2, flattened to root).
 */
public record AiNewsCommentRequest(
        String content,
        Long parentId
) {
}
