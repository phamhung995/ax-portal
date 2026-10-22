package com.skillmanagement.feedback.dto;

import java.time.Instant;

/**
 * Full feedback detail (admin). Includes the screenshot data URI and
 * resolution info.
 */
public record FeedbackDetailResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        String type,
        String content,
        String screenshot,
        String status,
        String resolutionNote,
        String resolvedByName,
        Instant resolvedAt,
        Instant createdAt
) {}
