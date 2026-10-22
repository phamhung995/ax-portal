package com.skillmanagement.feedback.dto;

/**
 * Admin status change: IN_REVIEW / RESOLVED / IGNORED.
 * resolutionNote is required for RESOLVED and IGNORED.
 */
public record FeedbackStatusRequest(
        String status,
        String resolutionNote
) {}
