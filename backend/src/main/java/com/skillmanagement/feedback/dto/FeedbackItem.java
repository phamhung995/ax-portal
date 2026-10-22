package com.skillmanagement.feedback.dto;

import java.time.Instant;

/**
 * One feedback row in the admin grid. Screenshot is NOT included here
 * (it can be a multi-MB base64 string) — fetch it in the detail call.
 */
public record FeedbackItem(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        String type,
        String content,
        String status,
        Instant createdAt,
        boolean hasScreenshot,
        String resolvedByName
) {}
