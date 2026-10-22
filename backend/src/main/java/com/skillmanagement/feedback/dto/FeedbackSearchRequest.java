package com.skillmanagement.feedback.dto;

/**
 * Admin search criteria for the feedback grid.
 * All filters optional; null/blank means "any".
 */
public record FeedbackSearchRequest(
        String type,
        String status,
        String content,
        Long requesterId,
        Long resolvedById,
        int page,
        int pageSize
) {}
