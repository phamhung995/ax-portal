package com.skillmanagement.feedback.dto;

/**
 * Submit feedback (VOC). Screenshot is an optional base64 data URI.
 */
public record FeedbackRequest(
        String type,
        String content,
        String screenshot
) {}
